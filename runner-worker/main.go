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
	"sort"
	"strconv"
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
	ExecutionWallTimeMs     int64    `json:"executionWallTimeMs"`
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
const executionTimingMarker = "__BENCHLAB_EXECUTION_WALL_TIME_US__"
const cpuTimingMarker = "__BENCHLAB_CPU_TIME_US__"

var callbackHTTPClient = &http.Client{Timeout: 10 * time.Second}

func main() {
	if len(os.Args) > 1 && os.Args[1] == "--measure-execution" {
		os.Exit(measureExecutionCommand(os.Args[2:]))
	}

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
	if err := installMeasurementHelper(tempDir); err != nil {
		return failedResult(host, "FAILED", fmt.Sprintf("no se pudo preparar el medidor de ejecucion: %v", err))
	}

	runnerSpec, err := prepareRunnerSpec(strings.ToUpper(event.Language), event.SourceCode, tempDir)
	if err != nil {
		return failedResult(host, "FAILED", err.Error())
	}

	compileWallTimeMs := int64(0)
	if len(runnerSpec.compileCommand) > 0 {
		compileEvent := event
		compileEvent.TimeoutMs = compilationTimeoutMs()
		compileResult := runDockerCommand(compileEvent, runnerSpec, tempDir, runnerSpec.compileCommand, true, false)
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
		warmupResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.runCommand, false, false)
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
	elapsedSamples := make([]int64, 0, event.Iterations)
	executionSamples := make([]int64, 0, event.Iterations)
	cpuSamples := make([]int64, 0, event.Iterations)
	totalOutputBytes := int64(0)
	lastOutStr := ""
	lastErrStr := ""
	stdoutTruncated := false
	stderrTruncated := false
	expectedStdout := ""
	expectedStderr := ""

	for i := 0; i < event.Iterations; i++ {
		runResult := runDockerCommand(event, runnerSpec, tempDir, runnerSpec.runCommand, false, true)
		elapsedSamples = append(elapsedSamples, runResult.elapsedMs)
		executionSamples = append(executionSamples, runResult.executionElapsedMs)
		if runResult.cpuMeasured {
			cpuSamples = append(cpuSamples, runResult.cpuElapsedMs)
		}
		totalOutputBytes += runResult.outputSizeBytes
		lastOutStr = runResult.stdout
		lastErrStr = runResult.stderr
		stdoutTruncated = stdoutTruncated || runResult.stdoutTruncated
		stderrTruncated = stderrTruncated || runResult.stderrTruncated

		if runResult.err != nil {
			status, exitCode, failureReason, timedOut = classifyRunError(runResult.err, runResult.contextErr, "RUNTIME_ERROR")
			break
		}

		if i == 0 {
			expectedStdout = runResult.stdout
			expectedStderr = runResult.stderr
			continue
		}
		if runResult.stdout != expectedStdout || runResult.stderr != expectedStderr {
			status = "RUNTIME_ERROR"
			exitCode = 1
			failureReason = fmt.Sprintf("salida no determinista entre iteraciones medidas: iteracion=%d", i+1)
			break
		}
	}

	timing := summarizeDurations(elapsedSamples)
	executionTiming := summarizeDurations(executionSamples)
	cpuTiming := summarizeDurations(cpuSamples)
	checksum := sha256Of(lastOutStr + lastErrStr)
	return RunResultCallbackRequest{
		Status:                  status,
		RunnerHost:              host,
		FailureReason:           failureReason,
		CpuTimeMs:               optionalMedian(cpuTiming),
		OrchestrationWallTimeMs: timing.medianMs,
		ExecutionWallTimeMs:     executionTiming.medianMs,
		PeakMemoryMb:            nil,
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
			"language=%s datasetVersion=%s timingMode=isolated-process wallMetric=median cpuMetric=child-process-user-system-time warmup=%d iterations=%d samples=%d minMs=%d medianMs=%d meanMs=%d maxMs=%d orchestrationMedianMs=%d cpuMedianMs=%s",
			event.Language,
			event.DatasetVersion,
			event.WarmupIterations,
			event.Iterations,
			timing.count,
			timing.minMs,
			timing.medianMs,
			timing.meanMs,
			timing.maxMs,
			timing.medianMs,
			formatOptionalMedian(cpuTiming),
		),
	}
}

type durationSummary struct {
	count    int
	minMs    int64
	medianMs int64
	meanMs   int64
	maxMs    int64
}

func summarizeDurations(samples []int64) durationSummary {
	if len(samples) == 0 {
		return durationSummary{}
	}

	sortedSamples := append([]int64(nil), samples...)
	sort.Slice(sortedSamples, func(i, j int) bool {
		return sortedSamples[i] < sortedSamples[j]
	})

	total := int64(0)
	for _, sample := range sortedSamples {
		total += sample
	}

	middle := len(sortedSamples) / 2
	median := sortedSamples[middle]
	if len(sortedSamples)%2 == 0 {
		median = (sortedSamples[middle-1] + sortedSamples[middle]) / 2
	}

	return durationSummary{
		count:    len(sortedSamples),
		minMs:    sortedSamples[0],
		medianMs: median,
		meanMs:   total / int64(len(sortedSamples)),
		maxMs:    sortedSamples[len(sortedSamples)-1],
	}
}

func optionalMedian(summary durationSummary) *int64 {
	if summary.count == 0 {
		return nil
	}
	median := summary.medianMs
	return &median
}

func formatOptionalMedian(summary durationSummary) string {
	if summary.count == 0 {
		return "unavailable"
	}
	return strconv.FormatInt(summary.medianMs, 10)
}

type dockerRunnerSpec struct {
	image          string
	compileCommand []string
	runCommand     []string
}

type dockerRunResult struct {
	elapsedMs          int64
	executionElapsedMs int64
	cpuElapsedMs       int64
	cpuMeasured        bool
	stdout             string
	stderr             string
	stdoutTruncated    bool
	stderrTruncated    bool
	outputSizeBytes    int64
	err                error
	contextErr         error
}

func prepareRunnerSpec(language, sourceCode, tempDir string) (dockerRunnerSpec, error) {
	switch language {
	case "PYTHON":
		err := os.WriteFile(filepath.Join(tempDir, "main.py"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo python: %w", err)
		}
		return dockerRunnerSpec{image: runnerImage("RUNNER_IMAGE_PYTHON", "python:3.12-alpine"), runCommand: []string{"python", "/workspace/main.py"}}, nil
	case "JAVA":
		err := os.WriteFile(filepath.Join(tempDir, "Main.java"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo java: %w", err)
		}
		compileCmd := []string{"sh", "-lc", "mkdir -p classes && javac -d classes Main.java"}
		runCmd := []string{"java", "-cp", "/workspace/classes", "Main"}
		return dockerRunnerSpec{image: runnerImage("RUNNER_IMAGE_JAVA", "eclipse-temurin:21-jdk"), compileCommand: compileCmd, runCommand: runCmd}, nil
	case "GO":
		err := os.WriteFile(filepath.Join(tempDir, "main.go"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo go: %w", err)
		}
		compileCmd := []string{"sh", "-c", "GOCACHE=/tmp/go-build go build -trimpath -o benchlab-app main.go"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: runnerImage("RUNNER_IMAGE_GO", "golang:1.22-alpine"), compileCommand: compileCmd, runCommand: runCmd}, nil
	case "RUST":
		err := os.WriteFile(filepath.Join(tempDir, "main.rs"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo rust: %w", err)
		}
		compileCmd := []string{"sh", "-lc", "PATH=/usr/local/cargo/bin:$PATH rustc -C opt-level=2 -o benchlab-app main.rs"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: runnerImage("RUNNER_IMAGE_RUST", "rust:1.87-bookworm"), compileCommand: compileCmd, runCommand: runCmd}, nil
	case "ASSEMBLY":
		err := os.WriteFile(filepath.Join(tempDir, "main.s"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo assembly: %w", err)
		}
		compileCmd := []string{"sh", "-lc", "gcc -x assembler -o benchlab-app main.s"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: runnerImage("RUNNER_IMAGE_CPP", "gcc:14"), compileCommand: compileCmd, runCommand: runCmd}, nil
	case "RUBY":
		err := os.WriteFile(filepath.Join(tempDir, "main.rb"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo ruby: %w", err)
		}
		return dockerRunnerSpec{image: runnerImage("RUNNER_IMAGE_RUBY", "ruby:3.3-alpine"), runCommand: []string{"ruby", "/workspace/main.rb"}}, nil
	case "C":
		err := os.WriteFile(filepath.Join(tempDir, "main.c"), []byte(sourceCode), 0o600)
		if err != nil {
			return dockerRunnerSpec{}, fmt.Errorf("no se pudo escribir archivo c: %w", err)
		}
		compileCmd := []string{"gcc", "main.c", "-O2", "-o", "benchlab-app"}
		runCmd := []string{"/workspace/benchlab-app"}
		return dockerRunnerSpec{image: runnerImage("RUNNER_IMAGE_C", "gcc:14"), compileCommand: compileCmd, runCommand: runCmd}, nil
	default:
		return dockerRunnerSpec{}, fmt.Errorf("lenguaje no soportado en fase actual: %s", language)
	}
}

func runnerImage(environmentKey, fallback string) string {
	if configured := strings.TrimSpace(os.Getenv(environmentKey)); configured != "" {
		return configured
	}
	return fallback
}

func runDockerCommand(event RunRequestedEvent, runnerSpec dockerRunnerSpec, tempDir string, command []string, writableWorkspace bool, measureExecution bool) dockerRunResult {
	started := time.Now()
	containerName := fmt.Sprintf("benchlab-%d-%d", os.Getpid(), started.UnixNano())
	defer func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cleanupCancel()
		if err := exec.CommandContext(cleanupCtx, "docker", "rm", "-f", containerName).Run(); err != nil {
			log.Printf("No se pudo confirmar limpieza del contenedor %s", containerName)
		}
	}()
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(event.TimeoutMs)*time.Millisecond)
	defer cancel()

	args := []string{
		"run", "--name", containerName, "--network", "none", "--read-only",
		"--tmpfs", "/tmp:rw,exec,nosuid,size=64m",
		"--memory", fmt.Sprintf("%dm", event.MemoryMb),
		"--cpus", fmt.Sprintf("%.2f", event.CpuLimit),
		"-e", fmt.Sprintf("BENCHLAB_DATASET_SIZE=%d", event.DatasetSize),
		"-e", fmt.Sprintf("BENCHLAB_DATASET_SEED=%d", event.DatasetSeed),
		"-v", dockerVolumeMount(tempDir, !writableWorkspace),
		"-w", "/workspace",
		runnerSpec.image,
	}
	if measureExecution {
		args = append(args, "/workspace/benchlab-timer", "--measure-execution")
	}
	args = append(args, command...)

	cmd := exec.CommandContext(ctx, "docker", args...)
	stdout := newBoundedCapture(outputLimit)
	stderr := newBoundedCapture(outputLimit)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	runErr := cmd.Run()
	stderrValue := stderr.String()
	executionElapsedMs := int64(0)
	cpuElapsedMs := int64(0)
	cpuMeasured := false
	if measureExecution {
		var measured bool
		stderrValue, executionElapsedMs, measured = extractExecutionTiming(stderrValue)
		if !measured && runErr == nil {
			runErr = errors.New("no se pudo leer el tiempo interno de ejecucion")
		}
		cpuElapsedMs, cpuMeasured = extractCpuTiming(stderr.String())
	}

	return dockerRunResult{
		elapsedMs:          time.Since(started).Milliseconds(),
		executionElapsedMs: executionElapsedMs,
		cpuElapsedMs:       cpuElapsedMs,
		cpuMeasured:        cpuMeasured,
		stdout:             stdout.String(),
		stderr:             stderrValue,
		stdoutTruncated:    stdout.Truncated(),
		stderrTruncated:    stderr.Truncated(),
		outputSizeBytes:    stdout.TotalBytes() + stderr.TotalBytes(),
		err:                runErr,
		contextErr:         ctx.Err(),
	}
}

func installMeasurementHelper(tempDir string) error {
	executable, err := os.Executable()
	if err != nil {
		return err
	}
	contents, err := os.ReadFile(executable)
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(tempDir, "benchlab-timer"), contents, 0o700)
}

func measureExecutionCommand(command []string) int {
	if len(command) == 0 {
		fmt.Fprintf(os.Stderr, "%s=0\n", executionTimingMarker)
		return 2
	}

	started := time.Now()
	startedCPU, cpuAvailable := childCPUTimeMicros()
	child := exec.Command(command[0], command[1:]...)
	child.Stdout = os.Stdout
	child.Stderr = os.Stderr
	err := child.Run()
	finishedCPU, finishedCPUAvailable := childCPUTimeMicros()
	fmt.Fprintf(os.Stderr, "\n%s=%d\n", executionTimingMarker, time.Since(started).Microseconds())
	if cpuAvailable && finishedCPUAvailable && finishedCPU >= startedCPU {
		fmt.Fprintf(os.Stderr, "%s=%d\n", cpuTimingMarker, finishedCPU-startedCPU)
	} else {
		fmt.Fprintf(os.Stderr, "%s=-1\n", cpuTimingMarker)
	}
	if err == nil {
		return 0
	}
	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		return exitError.ExitCode()
	}
	return 1
}

func extractCpuTiming(stderr string) (int64, bool) {
	marker := cpuTimingMarker + "="
	markerIndex := strings.LastIndex(stderr, marker)
	if markerIndex < 0 {
		return 0, false
	}
	valueStart := markerIndex + len(marker)
	valueEnd := strings.IndexByte(stderr[valueStart:], '\n')
	if valueEnd < 0 {
		valueEnd = len(stderr) - valueStart
	}
	micros, err := strconv.ParseInt(strings.TrimSpace(stderr[valueStart:valueStart+valueEnd]), 10, 64)
	if err != nil || micros < 0 {
		return 0, false
	}
	return max(0, (micros+999)/1000), true
}

func extractExecutionTiming(stderr string) (string, int64, bool) {
	marker := executionTimingMarker + "="
	markerIndex := strings.LastIndex(stderr, marker)
	if markerIndex < 0 {
		return stderr, 0, false
	}
	valueStart := markerIndex + len(marker)
	valueEnd := strings.IndexByte(stderr[valueStart:], '\n')
	if valueEnd < 0 {
		valueEnd = len(stderr) - valueStart
	}
	micros, err := strconv.ParseInt(strings.TrimSpace(stderr[valueStart:valueStart+valueEnd]), 10, 64)
	if err != nil || micros < 0 {
		return stderr, 0, false
	}
	cleanEnd := markerIndex
	if cleanEnd > 0 && stderr[cleanEnd-1] == '\n' {
		cleanEnd--
	}
	return stderr[:cleanEnd], max(1, (micros+999)/1000), true
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
		return "TIMEOUT", 1, fmt.Sprintf("fase %s excedio timeout", defaultStatus), true
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
