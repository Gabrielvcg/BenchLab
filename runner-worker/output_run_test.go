package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"
)

// The real executeRun launches this test binary as a finite Docker substitute.
func init() {
	if os.Getenv("BENCHLAB_CAPTURE_FAKE") != "1" {
		return
	}
	if os.Getenv("BENCHLAB_CAPTURE_MODE") == "cancel" {
		file, _ := os.OpenFile(os.Getenv("BENCHLAB_CAPTURE_STATE"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
		fmt.Fprintln(file, strings.Join(os.Args[1:], " "))
		file.Close()
		if len(os.Args) > 1 && os.Args[1] == "run" {
			time.Sleep(10 * time.Second)
		}
		os.Exit(0)
	}
	if len(os.Args) < 2 || os.Args[1] != "run" {
		os.Exit(0)
	}
	state := os.Getenv("BENCHLAB_CAPTURE_STATE")
	prior, _ := os.ReadFile(state)
	iteration := len(prior)
	if err := os.WriteFile(state, append(prior, 'x'), 0600); err != nil {
		os.Exit(2)
	}
	mode := os.Getenv("BENCHLAB_CAPTURE_MODE")
	out, errout := "same", "diagnostic"
	switch mode {
	case "tail_stdout":
		out = strings.Repeat("a", 8000) + strconv.Itoa(iteration)
	case "tail_stderr":
		errout = strings.Repeat("e", 8000) + strconv.Itoa(iteration)
	case "long_stderr":
		errout = strings.Repeat("e", 16000)
	case "marker_like":
		errout = "user\n__BENCHLAB_EXECUTION_WALL_TIME_US__=999\n__BENCHLAB_CPU_TIME_US__=777\n"
	case "boundary":
		if iteration == 0 {
			out = "ab"
			errout = "c"
		} else {
			out = "a"
			errout = "bc"
		}
	}
	for len(out) > 0 {
		n := min(17, len(out))
		fmt.Fprint(os.Stdout, out[:n])
		out = out[n:]
	}
	for len(errout) > 0 {
		n := min(13, len(errout))
		fmt.Fprint(os.Stderr, errout[:n])
		errout = errout[n:]
	}
	token := ""
	for i, arg := range os.Args {
		if arg == "--measure-execution" && i+1 < len(os.Args) {
			candidate := os.Args[i+1]
			if len(candidate) == 32 {
				token = candidate
			}
		}
	}
	if mode == "missing" || mode == "failed_missing" {
		if mode == "failed_missing" {
			os.Exit(7)
		}
		os.Exit(0)
	}
	if mode == "malformed" {
		fmt.Fprint(os.Stderr, "\n__BENCHLAB_METRICS_V1__ "+token+" bad 1\n")
		os.Exit(0)
	}
	if token != "" {
		fmt.Fprintf(os.Stderr, "\n__BENCHLAB_METRICS_V1__ %s %d %d\n", token, 1000+iteration*1000, 1000+iteration*1000)
	} else {
		fmt.Fprintf(os.Stderr, "\n__BENCHLAB_EXECUTION_WALL_TIME_US__=%d\n__BENCHLAB_CPU_TIME_US__=%d\n", 1000+iteration*1000, 1000+iteration*1000)
	}
	os.Exit(0)
}

func TestExecuteRunCompleteStreams(t *testing.T) {
	executable, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	binary, err := os.ReadFile(executable)
	if err != nil {
		t.Fatal(err)
	}
	fakeDir := t.TempDir()
	name := "docker"
	if filepath.Ext(executable) == ".exe" {
		name += ".exe"
	}
	if err := os.WriteFile(filepath.Join(fakeDir, name), binary, 0700); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", fakeDir+string(os.PathListSeparator)+os.Getenv("PATH"))
	t.Setenv("BENCHLAB_CAPTURE_FAKE", "1")
	for _, tc := range []struct {
		mode, status     string
		bytes            int64
		outFlag, errFlag bool
	}{
		{"tail_stdout", "RUNTIME_ERROR", 16022, true, false},
		{"tail_stderr", "RUNTIME_ERROR", 16010, false, true},
		{"long_stderr", "SUCCEEDED", 32008, false, true},
		{"timing_change", "SUCCEEDED", 28, false, false},
		{"marker_like", "SUCCEEDED", 0, false, false},
		{"boundary", "RUNTIME_ERROR", 6, false, false},
	} {
		t.Run(tc.mode, func(t *testing.T) {
			t.Setenv("BENCHLAB_CAPTURE_MODE", tc.mode)
			t.Setenv("BENCHLAB_CAPTURE_STATE", filepath.Join(t.TempDir(), "count"))
			r := executeRun(RunRequestedEvent{JobID: 1, Language: "PYTHON", SourceCode: "print('fixture')", TimeoutMs: 3000, MemoryMb: 128, CpuLimit: 1, Iterations: 2})
			if r.Status != tc.status {
				t.Fatalf("status=%s want=%s reason=%s", r.Status, tc.status, r.FailureReason)
			}
			if len(r.StdoutPreview) > 8000 || len(r.StderrPreview) > 8000 || r.StdoutTruncated != tc.outFlag || r.StderrTruncated != tc.errFlag {
				t.Fatalf("preview bounds/flags: %+v", r)
			}
			if tc.bytes != 0 && r.OutputSizeBytes != tc.bytes {
				t.Errorf("user bytes=%d want=%d", r.OutputSizeBytes, tc.bytes)
			}
			if tc.mode == "marker_like" && r.StderrPreview != "user\n__BENCHLAB_EXECUTION_WALL_TIME_US__=999\n__BENCHLAB_CPU_TIME_US__=777\n" {
				t.Fatal("user marker-like text was removed", r.StderrPreview)
			}
			expectedOut, expectedErr := "same", "diagnostic"
			switch tc.mode {
			case "tail_stdout":
				expectedOut = strings.Repeat("a", 8000) + "1"
			case "tail_stderr":
				expectedErr = strings.Repeat("e", 8000) + "1"
			case "long_stderr":
				expectedErr = strings.Repeat("e", 16000)
			case "marker_like":
				expectedErr = "user\n__BENCHLAB_EXECUTION_WALL_TIME_US__=999\n__BENCHLAB_CPU_TIME_US__=777\n"
			case "boundary":
				expectedOut, expectedErr = "a", "bc"
			}
			expectedChecksum := sha256Of("benchlab-output-v1\nstdout-sha256=" + sha256Of(expectedOut) + "\nstderr-sha256=" + sha256Of(expectedErr) + "\n")
			if r.ArtifactChecksum != expectedChecksum {
				t.Fatal("artifact checksum does not identify the complete final user streams", r.ArtifactChecksum, expectedChecksum)
			}
			if r.CpuTimeMs == nil {
				t.Fatal("CPU timing lost despite valid frame")
			}

		})
	}
}

func TestExecuteRunMissingOrMalformedMeasurement(t *testing.T) {
	executable, _ := os.Executable()
	binary, _ := os.ReadFile(executable)
	dir := t.TempDir()
	name := "docker"
	if filepath.Ext(executable) == ".exe" {
		name += ".exe"
	}
	if err := os.WriteFile(filepath.Join(dir, name), binary, 0700); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", dir+string(os.PathListSeparator)+os.Getenv("PATH"))
	t.Setenv("BENCHLAB_CAPTURE_FAKE", "1")
	for _, mode := range []string{"missing", "malformed", "failed_missing"} {
		t.Run(mode, func(t *testing.T) {
			t.Setenv("BENCHLAB_CAPTURE_STATE", filepath.Join(t.TempDir(), "count"))
			t.Setenv("BENCHLAB_CAPTURE_MODE", mode)
			r := executeRun(RunRequestedEvent{JobID: 1, Language: "PYTHON", SourceCode: "fixture", TimeoutMs: 3000, MemoryMb: 128, CpuLimit: 1, Iterations: 1})
			if r.Status != "RUNTIME_ERROR" || r.TimedOut {
				t.Fatal(r)
			}
			if mode == "failed_missing" {
				if r.ExitCode != 7 {
					t.Fatal("original exit code lost", r)
				}
			} else if !strings.Contains(r.FailureReason, "tiempo interno") {
				t.Fatal("missing timing not identified", r)
			}
		})
	}
}

func TestCaptureChangePreservesDeadlineAndNamedCleanup(t *testing.T) {
	executable, _ := os.Executable()
	binary, _ := os.ReadFile(executable)
	dir := t.TempDir()
	name := "docker"
	if filepath.Ext(executable) == ".exe" {
		name += ".exe"
	}
	if err := os.WriteFile(filepath.Join(dir, name), binary, 0700); err != nil {
		t.Fatal(err)
	}
	log := filepath.Join(dir, "calls")
	t.Setenv("PATH", dir+string(os.PathListSeparator)+os.Getenv("PATH"))
	t.Setenv("BENCHLAB_CAPTURE_FAKE", "1")
	t.Setenv("BENCHLAB_CAPTURE_MODE", "cancel")
	t.Setenv("BENCHLAB_CAPTURE_STATE", log)
	r := runDockerCommand(RunRequestedEvent{TimeoutMs: 1500, MemoryMb: 128, CpuLimit: 1}, dockerRunnerSpec{image: "fixture"}, dir, []string{"fixture"}, false, true)
	if !errors.Is(r.contextErr, context.DeadlineExceeded) || r.err == nil {
		t.Fatal("deadline lost", r)
	}
	status, _, _, timed := classifyRunError(r.err, r.contextErr, "RUNTIME_ERROR")
	if status != "TIMEOUT" || !timed {
		t.Fatal("classification changed")
	}
	calls, err := os.ReadFile(log)
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSpace(string(calls)), "\n")
	if len(lines) != 2 {
		t.Fatal(string(calls))
	}
	run, cleanup := strings.Fields(lines[0]), strings.Fields(lines[1])
	if run[0] != "run" || run[1] != "--name" || cleanup[0] != "rm" || cleanup[1] != "-f" || cleanup[2] != run[2] {
		t.Fatal("named cleanup changed", string(calls))
	}
}
