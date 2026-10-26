package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
	"testing"
)

const testToken = "0123456789abcdef0123456789abcdef"

func captureMeasurement(input string, chunk, limit int) (boundedCapture, measurementTiming, bool) {
	user := newBoundedCapture(limit)
	capture := newMeasurementCapture(&user, testToken)
	for len(input) > 0 {
		n := min(chunk, len(input))
		_, _ = capture.Write([]byte(input[:n]))
		input = input[n:]
	}
	timing, ok := capture.finish()
	return user, timing, ok
}

func TestMeasurementFramingAndDigestAcrossChunksAndLimits(t *testing.T) {
	userText := strings.Repeat("large-user-output", 2000) + "\n" + formatMeasurementFooter(testToken, 9, 8) + "marker-like tail"
	for _, limit := range []int{0, 1, 8000, len(userText)} {
		for _, chunk := range []int{1, 2, 17, 255, 256, 257, 100000} {
			t.Run(fmt.Sprintf("limit%d_chunk%d", limit, chunk), func(t *testing.T) {
				user, timing, ok := captureMeasurement(userText+formatMeasurementFooter(testToken, 1234, 2345), chunk, limit)
				if !ok || timing.wallMicros != 1234 || timing.cpuMicros != 2345 {
					t.Fatal(timing, ok)
				}
				if user.String() != userText[:min(limit, len(userText))] || user.TotalBytes() != int64(len(userText)) || user.Truncated() != (len(userText) > limit) {
					t.Fatal("preview/count/flags")
				}
				if user.Digest() != sha256Of(userText) {
					t.Fatal("digest does not cover exact full user bytes")
				}
				changed, _, ok := captureMeasurement(userText+formatMeasurementFooter(testToken, 9000, -1), chunk, limit)
				if !ok || changed.Digest() != user.Digest() {
					t.Fatal("variable timing polluted user identity")
				}
			})
		}
	}
}

func TestMeasurementRejectsMalformedFinalFramesWithoutDiscardingUserBytes(t *testing.T) {
	valid := formatMeasurementFooter(testToken, 1234, 2345)
	cases := []string{
		"", "no frame", "__BENCHLAB_EXECUTION_WALL_TIME_US__=123\n",
		strings.TrimSuffix(valid, "\n"), valid + "trailing user text", valid + "\n",
		strings.Replace(valid, testToken, strings.Repeat("f", 32), 1),
		strings.Replace(valid, "1234", "not-a-number", 1),
		strings.Replace(valid, "1234", "-1", 1), strings.Replace(valid, "1234", "+1", 1),
		strings.Replace(valid, "2345", "-2", 1), strings.Replace(valid, "2345", "9223372036854775808", 1),
		strings.Replace(valid, "1234", "1 2", 1), strings.Replace(valid, "1234", "1\r", 1),
		strings.Replace(valid, "1234", "1.5", 1),
		valid + formatMeasurementFooter(testToken, -1, 10),
	}
	for i, input := range cases {
		t.Run(fmt.Sprintf("case%d", i), func(t *testing.T) {
			original := "user-data" + input
			user, _, ok := captureMeasurement(original, 1, 8000)
			if ok || user.String() != original || user.Digest() != sha256Of(original) || user.TotalBytes() != int64(len(original)) {
				t.Fatal("malformed frame lost/accepted", input)
			}
		})
	}
	// An earlier well-formed pair is ordinary user data; ONLY the final pair is stripped.
	user, timing, ok := captureMeasurement(valid+valid, 3, 8000)
	if !ok || timing.wallMicros != 1234 || user.String() != valid {
		t.Fatal("double frame semantics")
	}
	user, timing, ok = captureMeasurement("payload"+formatMeasurementFooter(testToken, 0, -1), 1, 8000)
	if !ok || user.String() != "payload" || timing.cpuMicros != -1 {
		t.Fatal("unavailable CPU/zero duration")
	}
}

func TestDigestSeparatesStreamsAndIgnoresChunking(t *testing.T) {
	a, b := newBoundedCapture(0), newBoundedCapture(0)
	_, _ = a.Write([]byte("ab"))
	_, _ = b.Write([]byte("c"))
	first := outputChecksum(a.Digest(), b.Digest())
	c, d := newBoundedCapture(1), newBoundedCapture(1)
	_, _ = c.Write([]byte("a"))
	_, _ = d.Write([]byte("b"))
	_, _ = d.Write([]byte("c"))
	if first == outputChecksum(c.Digest(), d.Digest()) {
		t.Fatal("stream boundary collision")
	}
	e := newBoundedCapture(1)
	_, _ = e.Write([]byte("a"))
	_, _ = e.Write([]byte("b"))
	if e.Digest() != a.Digest() || a.String() != "" || a.TotalBytes() != 2 || !a.Truncated() {
		t.Fatal("zero preview/chunk identity")
	}
}

func TestMeasurementTailStorageIsBounded(t *testing.T) {
	user := newBoundedCapture(0)
	capture := newMeasurementCapture(&user, testToken)
	_, _ = capture.Write([]byte(strings.Repeat("x", 1024*1024)))
	if len(capture.tail) > measurementTailLimit || cap(capture.tail) > measurementTailLimit {
		t.Fatal("unbounded tail")
	}
	_, _ = capture.Write([]byte(formatMeasurementFooter(testToken, 1, 1)))
	if _, ok := capture.finish(); !ok || user.TotalBytes() != 1024*1024 {
		t.Fatal("large write accounting")
	}
}

// Real helper and real child process, without Docker or Linux assumptions.
func TestMeasurementHelperProcess(t *testing.T) {
	if os.Getenv("BENCHLAB_REAL_MEASUREMENT_ROLE") != "helper" {
		return
	}
	exe, _ := os.Executable()
	os.Setenv("BENCHLAB_REAL_MEASUREMENT_ROLE", "user")
	os.Exit(measureExecutionCommand(testToken, []string{exe, "-test.run=^TestMeasurementUserProcess$"}))
}
func TestMeasurementUserProcess(t *testing.T) {
	if os.Getenv("BENCHLAB_REAL_MEASUREMENT_ROLE") != "user" {
		return
	}
	fmt.Fprint(os.Stdout, "actual-child-stdout")
	fmt.Fprint(os.Stderr, strings.Repeat("u", 12000)+formatMeasurementFooter(strings.Repeat("f", 32), 9, 8))
	if os.Getenv("BENCHLAB_REAL_CHILD_FAIL") == "1" {
		os.Exit(7)
	}
	os.Exit(0)
}
func TestActualMeasurementHelperLongStderrAndExitCode(t *testing.T) {
	for _, fail := range []bool{false, true} {
		t.Run(fmt.Sprint(fail), func(t *testing.T) {
			exe, err := os.Executable()
			if err != nil {
				t.Fatal(err)
			}
			cmd := exec.Command(exe, "-test.run=^TestMeasurementHelperProcess$")
			cmd.Env = append(os.Environ(), "BENCHLAB_REAL_MEASUREMENT_ROLE=helper")
			if fail {
				cmd.Env = append(cmd.Env, "BENCHLAB_REAL_CHILD_FAIL=1")
			}
			stdout := newBoundedCapture(8000)
			stderr := newBoundedCapture(8000)
			capture := newMeasurementCapture(&stderr, testToken)
			cmd.Stdout = &stdout
			cmd.Stderr = capture
			err = cmd.Run()
			if fail {
				if exit, ok := err.(*exec.ExitError); !ok || exit.ExitCode() != 7 {
					t.Fatal(err)
				}
			} else if err != nil {
				t.Fatal(err)
			}
			timing, ok := capture.finish()
			userErr := strings.Repeat("u", 12000) + formatMeasurementFooter(strings.Repeat("f", 32), 9, 8)
			if !ok || timing.wallMicros < 0 || timing.cpuMicros < -1 || stdout.String() != "actual-child-stdout" || stderr.Digest() != sha256Of(userErr) || stderr.TotalBytes() != int64(len(userErr)) || !stderr.Truncated() {
				t.Fatal("actual helper output", timing, ok)
			}
		})
	}
}
