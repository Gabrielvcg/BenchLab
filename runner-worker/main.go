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
	"sync/atomic"
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
	Status                  string   `json:"status"`
	RunnerHost              string   `json:"runnerHost"`
	FailureReason           string   `json:"failureReason,omitempty"`
	CpuTimeMs               *int64   `json:"cpuTimeMs"`
	OrchestrationWallTimeMs int64    `json:"orchestrationWallTimeMs"`
	PeakMemoryMb            *float64 `json:"peakMemoryMb"`
	ExitCode                int      `json:"exitCode"`
	TimedOut                bool     `json:"timedOut"`
	CompileWallTimeMs       int64    `json:"compileWallTimeMs"`
	StdoutPreview           string   `json:"stdoutPreview,omitempty"`
	StderrPreview           string   `json:"stderrPreview,omitempty"`
	StdoutTruncated         bool     `json:"stdoutTruncated"`
	StderrTruncated         bool     `json:"stderrTruncated"`
	OutputSizeBytes         int64    `json:"outputSizeBytes"`
	ArtifactChecksum        string   `json:"artifactChecksum,omitempty"`
	TechnicalLogSummary     string   `json:"technicalLogSummary,omitempty"`
}

const outputLimit = 8000

var callbackHTTPClient = &http.Client{Timeout: 10 * time.Second}

func main() {
	rabbitURL := envOrDefault("RABBITMQ_URL", "amqp://benchlab:benchlab@localhost:5672/")
	queueName := envOrDefault("RUN_REQUESTED_QUEUE", "benchlab.run.requested.q")
	apiBaseURL := envOrDefault("BENCHLAB_API_BASE_URL", "http://localhost:8080")
	workerToken := envOrDefault("BENCHLAB_WORKER_TOKEN", "benchlab-internal-token")
	healthAddress := envOrDefault("WORKER_HEALTH_ADDRESS", ":8081")
	ready := &atomic.Bool{}
	go serveHealth(healthAddress, ready)

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

	ready.Store(true)
	defer ready.Store(false)
	log.Println("worker listo. Esperando trabajos...")
	for msg := range msgs {
		var event RunRequestedEvent
		if err := json.Unmarshal(msg.Body, &event); err != nil {
			log.Printf("mensaje invalido: %v", err)
			_ = msg.Nack(false, false)
			continue
		}
		runnerHost := workerHost()
		if err := sendStarted(apiBaseURL, workerToken, event.JobID, runnerHost); err != nil {
			log.Printf("error marcando inicio del trabajo. jobId=%d: %v", event.JobID, err)
			_ = msg.Nack(false, true)
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
	host := workerHost()

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

	compileWallTimeMs := int64(0)
	if len(runnerSpec.compileCommand) > 0 {
		compileResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.compileCommand, true)
		compileWallTimeMs = compileResult.elapsedMs
		if compileResult.err != nil {
			status, exitCode, failureReason, timedOut := classifyRunError(compileResult.err, compileResult.contextErr, "COMPILE_ERROR")
			return RunResultCallbackRequest{
				Status:                  status,
				RunnerHost:              host,
				FailureReason:           failureReason,
				OrchestrationWallTimeMs: 0,
				ExitCode:                exitCode,
				TimedOut:                timedOut,
				CompileWallTimeMs:       compileWallTimeMs,
				StdoutPreview:           compileResult.stdout,
				StderrPreview:           compileResult.stderr,
				StdoutTruncated:         compileResult.stdoutTruncated,
				StderrTruncated:         compileResult.stderrTruncated,
				OutputSizeBytes:         compileResult.outputSizeBytes,
				ArtifactChecksum:        sha256Of(compileResult.stdout + compileResult.stderr),
				TechnicalLogSummary:     fmt.Sprintf("language=%s datasetVersion=%s compile failed", event.Language, event.DatasetVersion),
			}
		}
	}

	for i := 0; i < event.WarmupIterations; i++ {
		warmupResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.runCommand, false)
		if warmupResult.err != nil {
			status, exitCode, failureReason, timedOut := classifyRunError(warmupResult.err, warmupResult.contextErr, "RUNTIME_ERROR")
			return RunResultCallbackRequest{
				Status:                  status,
				RunnerHost:              host,
				FailureReason:           failureReason,
				OrchestrationWallTimeMs: 0,
				ExitCode:                exitCode,
				TimedOut:                timedOut,
				CompileWallTimeMs:       compileWallTimeMs,
				StdoutPreview:           warmupResult.stdout,
				StderrPreview:           warmupResult.stderr,
				StdoutTruncated:         warmupResult.stdoutTruncated,
				StderrTruncated:         warmupResult.stderrTruncated,
				OutputSizeBytes:         warmupResult.outputSizeBytes,
				ArtifactChecksum:        sha256Of(warmupResult.stdout + warmupResult.stderr),
				TechnicalLogSummary:     fmt.Sprintf("language=%s datasetVersion=%s warmup failed at iteration=%d", event.Language, event.DatasetVersion, i+1),
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
	stdoutTruncated := false
	stderrTruncated := false
	completedIterations := 0

	for i := 0; i < event.Iterations; i++ {
		runResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.runCommand, false)
		totalElapsed += runResult.elapsedMs
		totalOutputBytes += runResult.outputSizeBytes
		lastOutStr = runResult.stdout
		lastErrStr = runResult.stderr
		stdoutTruncated = stdoutTruncated || runResult.stdoutTruncated
		stderrTruncated = stderrTruncated || runResult.stderrTruncated
		completedIterations++

		if runResult.err != nil {
			status, exitCode, failureReason, timedOut = classifyRunError(runResult.err, runResult.contextErr, "RUNTIME_ERROR")
			break
		}
	}

	avgElapsed := int64(0)
	if completedIterations > 0 {
		avgElapsed = totalElapsed / int64(completedIterations)
	}
	checksum := sha256Of(lastOutStr + lastErrStr)
	return RunResultCallbackRequest{
		Status:                  status,
		RunnerHost:              host,
		FailureReason:           failureReason,
		OrchestrationWallTimeMs: avgElapsed,
		ExitCode:                exitCode,
		TimedOut:                timedOut,
		CompileWallTimeMs:       compileWallTimeMs,
		StdoutPreview:           lastOutStr,
		StderrPreview:           lastErrStr,
		StdoutTruncated:         stdoutTruncated,
		StderrTruncated:         stderrTruncated,
		OutputSizeBytes:         totalOutputBytes,
		ArtifactChecksum:        checksum,
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
	elapsedMs       int64
	stdout          string
	stderr          string
	stdoutTruncated bool
	stderrTruncated bool
	outputSizeBytes int64
	err             error
	contextErr      error
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
		compileCmd := []string{"sh", "-lc", "PATH=/usr/local/cargo/bin:$PATH rustc -C opt-level=3 -o benchlab-app main.rs"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: "rust:1.87-bookworm", compileCommand: compileCmd, runCommand: runCmd}, nil
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
	stdout := newBoundedCapture(outputLimit)
	stderr := newBoundedCapture(outputLimit)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	runErr := cmd.Run()

	return dockerRunResult{
		elapsedMs:       time.Since(started).Milliseconds(),
		stdout:          stdout.String(),
		stderr:          stderr.String(),
		stdoutTruncated: stdout.Truncated(),
		stderrTruncated: stderr.Truncated(),
		outputSizeBytes: stdout.TotalBytes() + stderr.TotalBytes(),
		err:             runErr,
		contextErr:      ctx.Err(),
	}
}

type boundedCapture struct {
	buffer    bytes.Buffer
	limit     int
	total     int64
	truncated bool
}

func newBoundedCapture(limit int) boundedCapture {
	return boundedCapture{limit: max(limit, 0)}
}

func (capture *boundedCapture) Write(value []byte) (int, error) {
	capture.total += int64(len(value))
	remaining := capture.limit - capture.buffer.Len()
	if remaining > 0 {
		retained := min(len(value), remaining)
		_, _ = capture.buffer.Write(value[:retained])
	}
	if capture.total > int64(capture.limit) {
		capture.truncated = true
	}
	return len(value), nil
}

func (capture *boundedCapture) String() string {
	return capture.buffer.String()
}

func (capture *boundedCapture) TotalBytes() int64 {
	return capture.total
}

func (capture *boundedCapture) Truncated() bool {
	return capture.truncated
}

func serveHealth(address string, ready *atomic.Bool) {
	server := &http.Server{
		Addr:              address,
		Handler:           newHealthHandler(ready),
		ReadHeaderTimeout: 2 * time.Second,
	}
	log.Printf("servidor de salud iniciado en %s", address)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Printf("servidor de salud detenido por error: %v", err)
	}
}

func newHealthHandler(ready *atomic.Bool) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health/live", func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusOK)
		_, _ = response.Write([]byte("live"))
	})
	mux.HandleFunc("/health/ready", func(response http.ResponseWriter, _ *http.Request) {
		if !ready.Load() {
			http.Error(response, "not ready", http.StatusServiceUnavailable)
			return
		}
		response.WriteHeader(http.StatusOK)
		_, _ = response.Write([]byte("ready"))
	})
	return mux
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

	resp, err := callbackHTTPClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return fmt.Errorf("status=%d body=%s", resp.StatusCode, string(raw))
	}
	return nil
}

func sendStarted(apiBaseURL, workerToken string, runID int64, runnerHost string) error {
	url := fmt.Sprintf("%s/api/internal/runs/%d/start", strings.TrimSuffix(apiBaseURL, "/"), runID)
	req, err := http.NewRequest(http.MethodPost, url, nil)
	if err != nil {
		return err
	}
	req.Header.Set("X-Worker-Token", workerToken)
	req.Header.Set("X-Runner-Host", runnerHost)

	resp, err := callbackHTTPClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return fmt.Errorf("status=%d body=%s", resp.StatusCode, string(raw))
	}
	return nil
}

func workerHost() string {
	host, _ := os.Hostname()
	if host == "" {
		return "runner-worker"
	}
	return host
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
		Status:                  status,
		RunnerHost:              host,
		FailureReason:           reason,
		OrchestrationWallTimeMs: 0,
		ExitCode:                1,
		TimedOut:                false,
		CompileWallTimeMs:       0,
		OutputSizeBytes:         0,
		TechnicalLogSummary:     "error en worker",
	}
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
