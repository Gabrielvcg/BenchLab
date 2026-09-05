package main

import (
	"context"
	"errors"
	"os"
	"testing"
)

func TestCompilationBudgetIsBounded(t *testing.T) {
	for _, value := range []string{"", "invalid", "0", "300001"} {
		t.Setenv("WORKER_COMPILE_TIMEOUT_MS", value)
		if compilationTimeoutMs() != 120000 {
			t.Fatal("invalid default")
		}
	}
	t.Setenv("WORKER_COMPILE_TIMEOUT_MS", "60000")
	if compilationTimeoutMs() != 60000 {
		t.Fatal("ignored configuration")
	}
}

func TestTimeoutReportsCompilationPhase(t *testing.T) {
	status, _, reason, timedOut := classifyRunError(errors.New("killed"), context.DeadlineExceeded, "COMPILE_ERROR")
	if status != "TIMEOUT" || !timedOut || reason != "fase COMPILE_ERROR excedio timeout" {
		t.Fatal("missing timeout phase")
	}
}

func TestDockerCompileAndExecuteWithSeparateBudget(t *testing.T) {
	if os.Getenv("BENCHLAB_TEST_DOCKER") != "1" {
		t.Skip("Docker opt-in")
	}
	dir := t.TempDir()
	spec, err := prepareRunnerSpec("GO", "package main\nimport \"fmt\"\nfunc main(){fmt.Println(42)}", dir)
	if err != nil {
		t.Fatal(err)
	}
	event := RunRequestedEvent{TimeoutMs: compilationTimeoutMs(), MemoryMb: 256, CpuLimit: 1}
	compiled := runDockerCommand(event, spec, dir, spec.compileCommand, true, false)
	if compiled.err != nil {
		t.Fatalf("compilation failed: %v", compiled.err)
	}
	event.TimeoutMs = 15000
	run := runDockerCommand(event, spec, dir, spec.runCommand, false, false)
	if run.err != nil || run.stdout != "42\n" {
		t.Fatal("compiled program did not produce expected output")
	}
}
