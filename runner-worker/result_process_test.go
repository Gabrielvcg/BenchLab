package main

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"sync/atomic"
	"testing"
	"time"
)

// This helper runs in a separate OS process. Execution is a fixture, not Docker.
func TestJournalProcessHelper(t *testing.T) {
	if os.Getenv("BENCHLAB_JOURNAL_CHILD") != "1" {
		return
	}
	store, err := openResultStore(os.Getenv("BENCHLAB_JOURNAL_DIR"))
	if err != nil {
		t.Fatal(err)
	}
	retry := func(operation func() error) { retryResult(operation, time.Sleep) }
	send := func(id int64, result RunResultCallbackRequest) error {
		return sendResult(os.Getenv("BENCHLAB_JOURNAL_API"), "fixture-token", id, result)
	}
	if err := store.walk(func(record *completedResult) error { store.deliver(record, send, retry); return nil }); err != nil {
		t.Fatal(err)
	}
	err = store.process(RunRequestedEvent{JobID: 42}, func() error { return nil }, func(RunRequestedEvent) RunResultCallbackRequest {
		file, err := os.OpenFile(filepath.Join(store.dir, "executions.txt"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := file.WriteString("executed\n"); err != nil {
			t.Fatal(err)
		}
		if err := file.Sync(); err != nil {
			t.Fatal(err)
		}
		if err := file.Close(); err != nil {
			t.Fatal(err)
		}
		return RunResultCallbackRequest{Status: "SUCCEEDED", RunnerHost: "process-fixture", StdoutPreview: "persistent-result"}
	}, send, retry)
	if err != nil {
		t.Fatal(err)
	}
}

func TestJournalSurvivesKilledProcess(t *testing.T) {
	directory := t.TempDir()
	var recovered atomic.Bool
	var calls atomic.Int32
	failed := make(chan struct{}, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		if !recovered.Load() {
			w.WriteHeader(http.StatusServiceUnavailable)
			select {
			case failed <- struct{}{}:
			default:
			}
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()
	executable, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	child := func() (*exec.Cmd, *bytes.Buffer) {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		t.Cleanup(cancel)
		command := exec.CommandContext(ctx, executable, "-test.run=^TestJournalProcessHelper$")
		command.Env = append(os.Environ(), "BENCHLAB_JOURNAL_CHILD=1", "BENCHLAB_JOURNAL_DIR="+directory, "BENCHLAB_JOURNAL_API="+server.URL)
		output := &bytes.Buffer{}
		command.Stdout = output
		command.Stderr = output
		return command, output
	}
	first, output := child()
	if err := first.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if first.ProcessState == nil {
			_ = first.Process.Kill()
			_ = first.Wait()
		}
	})
	select {
	case <-failed:
	case <-time.After(15 * time.Second):
		_ = first.Process.Kill()
		_ = first.Wait()
		t.Fatal("child never reached callback", output.String())
	}
	persisted, err := os.ReadFile(filepath.Join(directory, "42.json"))
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(persisted, []byte("deliveredAt")) {
		t.Fatal("premature delivery marker")
	}
	if err := first.Process.Kill(); err != nil {
		t.Fatal(err)
	}
	if err := first.Wait(); err == nil {
		t.Fatal("expected forced process termination")
	}
	recovered.Store(true)
	second, secondOutput := child()
	if err := second.Run(); err != nil {
		t.Fatal(err, secondOutput.String())
	}
	executions, err := os.ReadFile(filepath.Join(directory, "executions.txt"))
	if err != nil || string(executions) != "executed\n" {
		t.Fatal("execution repeated", err, string(executions))
	}
	store, err := openResultStore(directory)
	if err != nil {
		t.Fatal(err)
	}
	record, err := store.load(42)
	if err != nil || record.DeliveredAt == nil || record.Result.StdoutPreview != "persistent-result" || calls.Load() < 2 {
		t.Fatal("recovery failed", record, err, calls.Load())
	}
}
