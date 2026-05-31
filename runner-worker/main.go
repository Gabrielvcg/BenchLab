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

	tempDir, err := os.MkdirTemp("", "benchlab-run-*")
	if err != nil {
		return failedResult(host, "FAILED", fmt.Sprintf("no se pudo crear dir temporal: %v", err))
	}
	defer os.RemoveAll(tempDir)

	runnerSpec, err := prepareRunnerSpec(strings.ToUpper(event.Language), event.SourceCode, tempDir)
	if err != nil {
		return failedResult(host, "FAILED", err.Error())
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
		started := time.Now()
		ctx, cancel := context.WithTimeout(context.Background(), time.Duration(event.TimeoutMs)*time.Millisecond)

		args := []string{
			"run", "--rm", "--network", "none", "--read-only",
			"--tmpfs", "/tmp:rw,exec,nosuid,size=64m",
			"--memory", fmt.Sprintf("%dm", event.MemoryMb),
			"--cpus", fmt.Sprintf("%.2f", event.CpuLimit),
			"-e", fmt.Sprintf("BENCHLAB_DATASET_SIZE=%d", event.DatasetSize),
			"-e", fmt.Sprintf("BENCHLAB_DATASET_SEED=%d", event.DatasetSeed),
			"-v", dockerVolumeMount(tempDir),
			"-w", "/workspace",
			runnerSpec.image,
		}
		args = append(args, runnerSpec.command...)

		cmd := exec.CommandContext(ctx, "docker", args...)
		var stdout bytes.Buffer
		var stderr bytes.Buffer
		cmd.Stdout = &stdout
		cmd.Stderr = &stderr
		runErr := cmd.Run()
		cancel()

		elapsed := time.Since(started).Milliseconds()
		totalElapsed += elapsed
		totalOutputBytes += int64(len(stdout.Bytes()) + len(stderr.Bytes()))
		lastOutStr = truncate(stdout.String(), outputLimit)
		lastErrStr = truncate(stderr.String(), outputLimit)

		if runErr != nil {
			status = "RUNTIME_ERROR"
			var exitErr *exec.ExitError
			switch {
			case errors.Is(ctx.Err(), context.DeadlineExceeded):
				status = "TIMEOUT"
				failureReason = "ejecucion excedio timeout"
				timedOut = true
			case errors.As(runErr, &exitErr):
				exitCode = exitErr.ExitCode()
				failureReason = fmt.Sprintf("proceso finalizo con codigo %d", exitCode)
			default:
				failureReason = runErr.Error()
			}
			break
		}
	}

	avgElapsed := totalElapsed / int64(event.Iterations)
	checksum := sha256Of(lastOutStr + lastErrStr)
	return RunResultCallbackRequest{
		Status:              status,
		RunnerHost:          host,
		FailureReason:       failureReason,
		CpuTimeMs:           avgElapsed,
		WallTimeMs:          avgElapsed,
		PeakMemoryMb:        float64(event.MemoryMb),
		ExitCode:            exitCode,
		TimedOut:            timedOut,
		CompileMs:           0,
		StdoutTruncated:     lastOutStr,
		StderrTruncated:     lastErrStr,
		OutputSizeBytes:     totalOutputBytes,
		ArtifactChecksum:    checksum,
		TechnicalLogSummary: fmt.Sprintf("language=%s datasetVersion=%s iterations=%d", event.Language, event.DatasetVersion, event.Iterations),
	}
}

type dockerRunnerSpec struct {
	image   string
	command []string
}

func prepareRunnerSpec(language, sourceCode, tempDir string) (dockerRunnerSpec, error) {
	switch language {
	case "PYTHON":
		err := os.WriteFile(filepath.Join(tempDir, "main.py"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo python: %w", err)
		}
		return dockerRunnerSpec{image: "python:3.12-alpine", command: []string{"python", "/workspace/main.py"}}, nil
	case "JAVA":
		err := os.WriteFile(filepath.Join(tempDir, "Main.java"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo java: %w", err)
		}
		cmd := []string{"sh", "-lc", "javac -d /tmp Main.java && java -cp /tmp Main"}
		return dockerRunnerSpec{image: "eclipse-temurin:21-jdk", command: cmd}, nil
	case "C":
		err := os.WriteFile(filepath.Join(tempDir, "main.c"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo c: %w", err)
		}
		cmd := []string{"sh", "-lc", "gcc main.c -O2 -o /tmp/app && /tmp/app"}
		return dockerRunnerSpec{image: "gcc:14", command: cmd}, nil
	default:
		return dockerRunnerSpec{}, fmt.Errorf("lenguaje no soportado en fase actual: %s", language)
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

func dockerVolumeMount(dir string) string {
	if runtime.GOOS == "windows" {
		clean := strings.ReplaceAll(dir, "\\", "/")
		return fmt.Sprintf("%s:/workspace:ro", clean)
	}
	return fmt.Sprintf("%s:/workspace:ro", dir)
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
