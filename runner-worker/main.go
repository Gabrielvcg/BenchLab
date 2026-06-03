package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type RunRequestedEvent struct {
	JobID            int64   `json:"jobId"`
	ImplementationID int64   `json:"implementationId"`
	DatasetID        int64   `json:"datasetId"`
	DatasetSize      int64   `json:"datasetSize"`
	DatasetSeed      int64   `json:"datasetSeed"`
	Language         string  `json:"language"`
	SourceCode       string  `json:"sourceCode"`
	CompileConfig    string  `json:"compileConfig"`
	RuntimeConfig    string  `json:"runtimeConfig"`
	DatasetVersion   string  `json:"datasetVersion"`
	TimeoutMs        int     `json:"timeoutMs"`
	MemoryMb         int     `json:"memoryMb"`
	CpuLimit         float64 `json:"cpuLimit"`
	Iterations       int     `json:"iterations"`
	WarmupIterations int     `json:"warmupIterations"`
	TraceID          string  `json:"traceId"`
}

type RunResultCallbackRequest struct {
	Status              string  `json:"status"`
	RunnerHost          string  `json:"runnerHost"`
	FailureReason       string  `json:"failureReason,omitempty"`
	CpuTimeMs           int64   `json:"cpuTimeMs"`
	WallTimeMs          int64   `json:"wallTimeMs"`
	PeakMemoryMb        float64 `json:"peakMemoryMb"`
	ExitCode            int     `json:"exitCode"`
	TimedOut            bool    `json:"timedOut"`
	CompileMs           int64   `json:"compileMs"`
	StdoutTruncated     string  `json:"stdoutTruncated,omitempty"`
	StderrTruncated     string  `json:"stderrTruncated,omitempty"`
	OutputSizeBytes     int64   `json:"outputSizeBytes"`
	ArtifactChecksum    string  `json:"artifactChecksum,omitempty"`
	TechnicalLogSummary string  `json:"technicalLogSummary,omitempty"`
}

const outputLimit = 8000

func main() {
	rabbitURL := envOrDefault("RABBITMQ_URL", "amqp://benchlab:benchlab@localhost:5672/")
	queueName := envOrDefault("RUN_REQUESTED_QUEUE", "benchlab.run.requested.q")
	apiBaseURL := envOrDefault("BENCHLAB_API_BASE_URL", "http://localhost:8080")
	workerToken := envOrDefault("BENCHLAB_WORKER_TOKEN", "benchlab-internal-token")

	conn, err := amqp.Dial(rabbitURL)
	if err != nil {
		log.Fatalf("no se pudo conectar a RabbitMQ: %v", err)
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		log.Fatalf("no se pudo abrir canal de RabbitMQ: %v", err)
	}
	defer ch.Close()

	msgs, err := ch.Consume(queueName, "benchlab-runner", false, false, false, false, nil)
	if err != nil {
		log.Fatalf("no se pudo consumir cola %s: %v", queueName, err)
	}

	log.Println("worker iniciado. Esperando jobs...")
	for msg := range msgs {
		var event RunRequestedEvent
		if err := json.Unmarshal(msg.Body, &event); err != nil {
			log.Printf("mensaje invalido: %v", err)
			_ = msg.Nack(false, false)
			continue
		}

		result := executeRun(event)
		if err := sendResult(apiBaseURL, workerToken, event.JobID, result); err != nil {
			log.Printf("error reportando resultado jobId=%d: %v", event.JobID, err)
			_ = msg.Nack(false, true)
			continue
		}

		log.Printf("job procesado correctamente. jobId=%d traceId=%s", event.JobID, event.TraceID)
		_ = msg.Ack(false)
	}
}

func executeRun(event RunRequestedEvent) RunResultCallbackRequest {
	host, _ := os.Hostname()
	if host == "" {
		host = "runner-worker"
	}

	if event.TimeoutMs <= 0 {
		event.TimeoutMs = 5000
	}
	if event.MemoryMb <= 0 {
		event.MemoryMb = 256
	}
	if event.CpuLimit <= 0 {
		event.CpuLimit = 1.0
	}
	if event.Iterations <= 0 {
		event.Iterations = 5
	}
	if event.WarmupIterations < 0 {
		event.WarmupIterations = 0
	}

	tempDir, err := os.MkdirTemp("", "benchlab-run-*")
	if err != nil {
		return failedResult(host, "FAILED", fmt.Sprintf("no se pudo crear dir temporal: %v", err))
	}
	defer os.RemoveAll(tempDir)

	runnerSpec, err := prepareRunnerSpec(strings.ToUpper(event.Language), event.SourceCode, tempDir)
	if err != nil {
		return failedResult(host, "FAILED", err.Error())
	}

	compileMs := int64(0)
	if len(runnerSpec.compileCommand) > 0 {
		compileResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.compileCommand, true)
		compileMs = compileResult.elapsedMs
		if compileResult.err != nil {
			status, exitCode, failureReason, timedOut := classifyRunError(compileResult.err, compileResult.contextErr, "COMPILE_ERROR")
			return RunResultCallbackRequest{
				Status:              status,
				RunnerHost:          host,
				FailureReason:       failureReason,
				CpuTimeMs:           0,
				WallTimeMs:          0,
				PeakMemoryMb:        float64(event.MemoryMb),
				ExitCode:            exitCode,
				TimedOut:            timedOut,
				CompileMs:           compileMs,
				StdoutTruncated:     truncate(compileResult.stdout, outputLimit),
				StderrTruncated:     truncate(compileResult.stderr, outputLimit),
				OutputSizeBytes:     int64(len(compileResult.stdout) + len(compileResult.stderr)),
				ArtifactChecksum:    sha256Of(compileResult.stdout + compileResult.stderr),
				TechnicalLogSummary: fmt.Sprintf("language=%s datasetVersion=%s compile failed", event.Language, event.DatasetVersion),
			}
		}
	}

	for i := 0; i < event.WarmupIterations; i++ {
		warmupResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.runCommand, false)
		if warmupResult.err != nil {
			status, exitCode, failureReason, timedOut := classifyRunError(warmupResult.err, warmupResult.contextErr, "RUNTIME_ERROR")
			return RunResultCallbackRequest{
				Status:              status,
				RunnerHost:          host,
				FailureReason:       failureReason,
				CpuTimeMs:           0,
				WallTimeMs:          0,
				PeakMemoryMb:        float64(event.MemoryMb),
				ExitCode:            exitCode,
				TimedOut:            timedOut,
				CompileMs:           compileMs,
				StdoutTruncated:     truncate(warmupResult.stdout, outputLimit),
				StderrTruncated:     truncate(warmupResult.stderr, outputLimit),
				OutputSizeBytes:     int64(len(warmupResult.stdout) + len(warmupResult.stderr)),
				ArtifactChecksum:    sha256Of(warmupResult.stdout + warmupResult.stderr),
				TechnicalLogSummary: fmt.Sprintf("language=%s datasetVersion=%s warmup failed at iteration=%d", event.Language, event.DatasetVersion, i+1),
			}
		}
	}

	status := "SUCCEEDED"
	exitCode := 0
	failureReason := ""
	timedOut := false
	totalElapsed := int64(0)
	totalOutputBytes := int64(0)
	lastOutStr := ""
	lastErrStr := ""

	for i := 0; i < event.Iterations; i++ {
		runResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.runCommand, false)
		totalElapsed += runResult.elapsedMs
		totalOutputBytes += int64(len(runResult.stdout) + len(runResult.stderr))
		lastOutStr = truncate(runResult.stdout, outputLimit)
		lastErrStr = truncate(runResult.stderr, outputLimit)

		if runResult.err != nil {
			status, exitCode, failureReason, timedOut = classifyRunError(runResult.err, runResult.contextErr, "RUNTIME_ERROR")
			break
		}
	}

	avgElapsed := totalElapsed / int64(event.Iterations)
	checksum := sha256Of(lastOutStr + lastErrStr)
	return RunResultCallbackRequest{
		Status:           status,
		RunnerHost:       host,
		FailureReason:    failureReason,
		CpuTimeMs:        avgElapsed,
		WallTimeMs:       avgElapsed,
		PeakMemoryMb:     float64(event.MemoryMb),
		ExitCode:         exitCode,
		TimedOut:         timedOut,
		CompileMs:        compileMs,
		StdoutTruncated:  lastOutStr,
		StderrTruncated:  lastErrStr,
		OutputSizeBytes:  totalOutputBytes,
		ArtifactChecksum: checksum,
		TechnicalLogSummary: fmt.Sprintf(
			"language=%s datasetVersion=%s warmup=%d iterations=%d",
			event.Language,
			event.DatasetVersion,
			event.WarmupIterations,
			event.Iterations,
		),
	}
}

type dockerRunnerSpec struct {
	image          string
	compileCommand []string
	runCommand     []string
}

type dockerRunResult struct {
	elapsedMs  int64
	stdout     string
	stderr     string
	err        error
	contextErr error
}

func prepareRunnerSpec(language, sourceCode, tempDir string) (dockerRunnerSpec, error) {
	switch language {
	case "PYTHON":
		err := os.WriteFile(filepath.Join(tempDir, "main.py"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo python: %w", err)
		}
		return dockerRunnerSpec{image: "python:3.12-alpine", runCommand: []string{"python", "/workspace/main.py"}}, nil
	case "JAVA":
		err := os.WriteFile(filepath.Join(tempDir, "Main.java"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo java: %w", err)
		}
		compileCmd := []string{"sh", "-lc", "mkdir -p classes && javac -d classes Main.java"}
		runCmd := []string{"java", "-cp", "/workspace/classes", "Main"}
		return dockerRunnerSpec{image: "eclipse-temurin:21-jdk", compileCommand: compileCmd, runCommand: runCmd}, nil
	case "GO":
		err := os.WriteFile(filepath.Join(tempDir, "main.go"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo go: %w", err)
		}
		compileCmd := []string{"sh", "-c", "GOCACHE=/tmp/go-build go build -trimpath -o benchlab-app main.go"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: "golang:1.22-alpine", compileCommand: compileCmd, runCommand: runCmd}, nil
	case "RUST":
		err := os.WriteFile(filepath.Join(tempDir, "main.rs"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo rust: %w", err)
		}
		compileCmd := []string{"sh", "-lc", "rustc -C opt-level=3 -o benchlab-app main.rs"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: "rust:1-bookworm", compileCommand: compileCmd, runCommand: runCmd}, nil
	case "ASSEMBLY":
		err := os.WriteFile(filepath.Join(tempDir, "main.s"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo assembly: %w", err)
		}
		compileCmd := []string{"sh", "-lc", "gcc -x assembler -o benchlab-app main.s"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: "gcc:14", compileCommand: compileCmd, runCommand: runCmd}, nil
	case "RUBY":
		err := os.WriteFile(filepath.Join(tempDir, "main.rb"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo ruby: %w", err)
		}
		return dockerRunnerSpec{image: "ruby:3.3-alpine", runCommand: []string{"ruby", "/workspace/main.rb"}}, nil
	case "C":
		err := os.WriteFile(filepath.Join(tempDir, "main.c"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo c: %w", err)
		}
		compileCmd := []string{"gcc", "main.c", "-O2", "-o", "benchlab-app"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: "gcc:14", compileCommand: compileCmd, runCommand: runCmd}, nil
	default:
		return dockerRunnerSpec{}, fmt.Errorf("lenguaje no soportado en fase actual: %s", language)
	}
}

func runDockerCommand(event RunRequestedEvent, runnerSpec dockerRunnerSpec, tempDir string, command []string, writableWorkspace bool) dockerRunResult {
	started := time.Now()
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(event.TimeoutMs)*time.Millisecond)
	defer cancel()

	args := []string{
		"run", "--rm", "--network", "none", "--read-only",
		"--tmpfs", "/tmp:rw,exec,nosuid,size=64m",
		"--memory", fmt.Sprintf("%dm", event.MemoryMb),
		"--cpus", fmt.Sprintf("%.2f", event.CpuLimit),
		"-e", fmt.Sprintf("BENCHLAB_DATASET_SIZE=%d", event.DatasetSize),
		"-e", fmt.Sprintf("BENCHLAB_DATASET_SEED=%d", event.DatasetSeed),
		"-v", dockerVolumeMount(tempDir, !writableWorkspace),
		"-w", "/workspace",
		runnerSpec.image,
	}
	args = append(args, command...)

	cmd := exec.CommandContext(ctx, "docker", args...)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	runErr := cmd.Run()

	return dockerRunResult{
		elapsedMs:  time.Since(started).Milliseconds(),
		stdout:     stdout.String(),
		stderr:     stderr.String(),
		err:        runErr,
		contextErr: ctx.Err(),
	}
}

func classifyRunError(runErr error, contextErr error, defaultStatus string) (string, int, string, bool) {
	var exitErr *exec.ExitError
	switch {
	case errors.Is(contextErr, context.DeadlineExceeded):
		return "TIMEOUT", 1, "ejecucion excedio timeout", true
	case errors.As(runErr, &exitErr):
		return defaultStatus, exitErr.ExitCode(), fmt.Sprintf("proceso finalizo con codigo %d", exitErr.ExitCode()), false
	default:
		return defaultStatus, 1, runErr.Error(), false
	}
}

func sendResult(apiBaseURL, workerToken string, runID int64, payload RunResultCallbackRequest) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	url := fmt.Sprintf("%s/api/internal/runs/%d/result", strings.TrimSuffix(apiBaseURL, "/"), runID)
	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Worker-Token", workerToken)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		raw, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("status=%d body=%s", resp.StatusCode, string(raw))
	}
	return nil
}

func dockerVolumeMount(dir string, readOnly bool) string {
	mode := "rw"
	if readOnly {
		mode = "ro"
	}
	if runtime.GOOS == "windows" {
		clean := strings.ReplaceAll(dir, "\\", "/")
		return fmt.Sprintf("%s:/workspace:%s", clean, mode)
	}
	return fmt.Sprintf("%s:/workspace:%s", dir, mode)
}

func failedResult(host, status, reason string) RunResultCallbackRequest {
	return RunResultCallbackRequest{
		Status:              status,
		RunnerHost:          host,
		FailureReason:       reason,
		CpuTimeMs:           0,
		WallTimeMs:          0,
		PeakMemoryMb:        0,
		ExitCode:            1,
		TimedOut:            false,
		CompileMs:           0,
		OutputSizeBytes:     0,
		TechnicalLogSummary: "error en worker",
	}
}

func truncate(value string, max int) string {
	if len(value) <= max {
		return value
	}
	return value[:max]
}

func sha256Of(value string) string {
	h := sha256.Sum256([]byte(value))
	return hex.EncodeToString(h[:])
}

func envOrDefault(key, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}
