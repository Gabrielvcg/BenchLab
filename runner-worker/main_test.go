package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync/atomic"
	"testing"
)

func TestBoundedCaptureRetainsOnlyLimitAndCountsAllBytes(t *testing.T) {
	capture := newBoundedCapture(8)

	written, err := capture.Write([]byte("hello"))
	if err != nil || written != 5 {
		t.Fatalf("unexpected first write: written=%d err=%v", written, err)
	}
	written, err = capture.Write([]byte(" world"))
	if err != nil || written != 6 {
		t.Fatalf("unexpected second write: written=%d err=%v", written, err)
	}

	if capture.String() != "hello wo" {
		t.Fatalf("unexpected retained output: %q", capture.String())
	}
	if capture.TotalBytes() != 11 {
		t.Fatalf("unexpected total byte count: %d", capture.TotalBytes())
	}
	if !capture.Truncated() {
		t.Fatal("expected capture to report truncation")
	}
}

func TestBoundedCaptureDoesNotReportTruncationAtExactLimit(t *testing.T) {
	capture := newBoundedCapture(4)

	_, _ = capture.Write([]byte("test"))

	if capture.String() != "test" || capture.TotalBytes() != 4 || capture.Truncated() {
		t.Fatalf("unexpected exact-limit state: output=%q total=%d truncated=%v", capture.String(), capture.TotalBytes(), capture.Truncated())
	}
}

func TestHealthHandlerSeparatesLivenessAndReadiness(t *testing.T) {
	ready := &atomic.Bool{}
	handler := newHealthHandler(ready)

	liveRecorder := httptest.NewRecorder()
	handler.ServeHTTP(liveRecorder, httptest.NewRequest(http.MethodGet, "/health/live", nil))
	if liveRecorder.Code != http.StatusOK {
		t.Fatalf("unexpected liveness status: %d", liveRecorder.Code)
	}

	notReadyRecorder := httptest.NewRecorder()
	handler.ServeHTTP(notReadyRecorder, httptest.NewRequest(http.MethodGet, "/health/ready", nil))
	if notReadyRecorder.Code != http.StatusServiceUnavailable {
		t.Fatalf("unexpected not-ready status: %d", notReadyRecorder.Code)
	}

	ready.Store(true)
	readyRecorder := httptest.NewRecorder()
	handler.ServeHTTP(readyRecorder, httptest.NewRequest(http.MethodGet, "/health/ready", nil))
	if readyRecorder.Code != http.StatusOK {
		t.Fatalf("unexpected ready status: %d", readyRecorder.Code)
	}
}

func TestPrepareRunnerSpecRustUsesCompilerImage(t *testing.T) {
	tempDir := t.TempDir()

	spec, err := prepareRunnerSpec("RUST", "fn main() { println!(\"ok\"); }", tempDir)
	if err != nil {
		t.Fatalf("prepareRunnerSpec returned error: %v", err)
	}

	if spec.image != "rust:1.87-bookworm" {
		t.Fatalf("unexpected rust image: %s", spec.image)
	}

	if len(spec.compileCommand) == 0 {
		t.Fatal("expected rust compile command")
	}

	if !strings.Contains(strings.Join(spec.compileCommand, " "), "/usr/local/cargo/bin") {
		t.Fatalf("expected rust compile command to include cargo bin path: %#v", spec.compileCommand)
	}

	if !strings.Contains(strings.Join(spec.compileCommand, " "), "opt-level=2") {
		t.Fatalf("expected rust compile command to use an optimization level aligned with C -O2: %#v", spec.compileCommand)
	}

	if len(spec.runCommand) != 1 || spec.runCommand[0] != "/workspace/benchlab-app" {
		t.Fatalf("unexpected rust run command: %#v", spec.runCommand)
	}

	if _, err := os.Stat(tempDir + string(os.PathSeparator) + "main.rs"); err != nil {
		t.Fatalf("expected rust source file to be created: %v", err)
	}
}

func TestSummarizeDurationsUsesMedianAndKeepsContext(t *testing.T) {
	summary := summarizeDurations([]int64{900, 500, 450, 470, 2000})

	if summary.count != 5 {
		t.Fatalf("unexpected sample count: %d", summary.count)
	}
	if summary.minMs != 450 {
		t.Fatalf("unexpected min: %d", summary.minMs)
	}
	if summary.medianMs != 500 {
		t.Fatalf("unexpected median: %d", summary.medianMs)
	}
	if summary.meanMs != 864 {
		t.Fatalf("unexpected mean: %d", summary.meanMs)
	}
	if summary.maxMs != 2000 {
		t.Fatalf("unexpected max: %d", summary.maxMs)
	}
}

func TestSummarizeDurationsHandlesEvenSamples(t *testing.T) {
	summary := summarizeDurations([]int64{500, 700, 900, 1100})

	if summary.medianMs != 800 {
		t.Fatalf("unexpected even-sample median: %d", summary.medianMs)
	}
}

func TestExtractExecutionTimingRemovesInternalMarker(t *testing.T) {
	clean, durationMs, measured := extractExecutionTiming("program diagnostic\n\n__BENCHLAB_EXECUTION_WALL_TIME_US__=1234\n")

	if !measured {
		t.Fatal("expected internal timing marker to be detected")
	}
	if clean != "program diagnostic\n" {
		t.Fatalf("unexpected cleaned stderr: %q", clean)
	}
	if durationMs != 2 {
		t.Fatalf("unexpected rounded duration: %d", durationMs)
	}
}

func TestExtractExecutionTimingRejectsMalformedMarker(t *testing.T) {
	clean, durationMs, measured := extractExecutionTiming("__BENCHLAB_EXECUTION_WALL_TIME_US__=not-a-number\n")

	if measured || durationMs != 0 || clean != "__BENCHLAB_EXECUTION_WALL_TIME_US__=not-a-number\n" {
		t.Fatalf("expected malformed marker to be rejected: clean=%q duration=%d measured=%v", clean, durationMs, measured)
	}
}

func TestExtractCpuTimingRoundsMicroseconds(t *testing.T) {
	durationMs, measured := extractCpuTiming("__BENCHLAB_CPU_TIME_US__=1234\n")

	if !measured {
		t.Fatal("expected CPU timing marker to be detected")
	}
	if durationMs != 2 {
		t.Fatalf("unexpected rounded CPU duration: %d", durationMs)
	}
}

func TestExtractCpuTimingRejectsUnavailableMarker(t *testing.T) {
	durationMs, measured := extractCpuTiming("__BENCHLAB_CPU_TIME_US__=-1\n")

	if measured || durationMs != 0 {
		t.Fatalf("expected unavailable CPU marker to be rejected: duration=%d measured=%v", durationMs, measured)
	}
}
