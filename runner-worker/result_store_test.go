package main

import (
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"
)

func testStore(t *testing.T) *resultStore {
	t.Helper()
	store, err := openResultStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	return store
}
func boundedRetry(t *testing.T) func(func() error) {
	return func(operation func() error) {
		t.Helper()
		for i := 0; i < 5; i++ {
			if operation() == nil {
				return
			}
		}
		t.Fatal("retry did not recover")
	}
}
func fixtureRecord(id int64) completedResult {
	body, _ := json.Marshal(RunRequestedEvent{JobID: id})
	return completedResult{Version: 1, JobID: id, EventHash: sha256Of(string(body)), Result: RunResultCallbackRequest{Status: "SUCCEEDED"}}
}

func TestCallbackRetryAndRedeliveryDoNotExecuteAgain(t *testing.T) {
	s := testStore(t)
	executions, starts, calls := 0, 0, 0
	event := RunRequestedEvent{JobID: 42}
	execute := func(RunRequestedEvent) RunResultCallbackRequest {
		executions++
		return RunResultCallbackRequest{Status: "SUCCEEDED", StdoutPreview: "saved"}
	}
	start := func() error { starts++; return nil }
	send := func(id int64, result RunResultCallbackRequest) error {
		calls++
		persisted, err := s.load(id)
		if err != nil || persisted == nil {
			t.Fatal("callback before persistence", err)
		}
		if result.StdoutPreview != "saved" {
			t.Fatal("changed payload")
		}
		if calls == 1 {
			return errors.New("API unavailable")
		}
		return nil
	}
	for i := 0; i < 2; i++ {
		if err := s.process(event, start, execute, send, boundedRetry(t)); err != nil {
			t.Fatal(err)
		}
	}
	if executions != 1 || starts != 1 || calls != 2 {
		t.Fatalf("execute/start/callback=%d/%d/%d", executions, starts, calls)
	}
	event.SourceCode = "different"
	if err := s.process(event, start, execute, send, boundedRetry(t)); err == nil {
		t.Fatal("conflicting event accepted")
	}
	if executions != 1 || calls != 2 {
		t.Fatal("conflict produced side effects")
	}
}

func TestRestartReplaysPersistedResultAndLostResponse(t *testing.T) {
	s := testStore(t)
	record := fixtureRecord(1)
	if err := s.save(record); err != nil {
		t.Fatal(err)
	}
	// Simulate API acceptance with lost response and process exit before marking delivery.
	accepted := 0
	send := func(int64, RunResultCallbackRequest) error { accepted++; return nil }
	if err := send(record.JobID, record.Result); err != nil {
		t.Fatal(err)
	}
	reopened, err := openResultStore(s.dir)
	if err != nil {
		t.Fatal(err)
	}
	if err := reopened.walk(func(record *completedResult) error {
		reopened.deliver(record, send, boundedRetry(t))
		return nil
	}); err != nil {
		t.Fatal(err)
	}
	err = reopened.process(RunRequestedEvent{JobID: 1}, func() error { t.Fatal("start repeated"); return nil }, func(RunRequestedEvent) RunResultCallbackRequest {
		t.Fatal("execute repeated")
		return RunResultCallbackRequest{}
	}, send, boundedRetry(t))
	if err != nil || accepted != 2 {
		t.Fatal(err, accepted)
	}
}

func TestPersistenceFailureRetainsComputedResult(t *testing.T) {
	s := testStore(t)
	executions, calls, failures := 0, 0, 0
	blocked := filepath.Join(s.dir, "1.json")
	retry := func(operation func() error) {
		if err := operation(); err == nil {
			t.Fatal("expected persistence failure")
		}
		failures++
		if calls != 0 {
			t.Fatal("sent before saved")
		}
		if err := os.Remove(blocked); err != nil {
			t.Fatal(err)
		}
		boundedRetry(t)(operation)
	}
	// The final delivered marker uses a normal retry; only the first save is obstructed.
	first := true
	wrapper := func(operation func() error) {
		if first {
			first = false
			retry(operation)
		} else {
			boundedRetry(t)(operation)
		}
	}
	err := s.process(RunRequestedEvent{JobID: 1}, func() error { return nil }, func(RunRequestedEvent) RunResultCallbackRequest {
		executions++
		if err := os.Mkdir(blocked, 0700); err != nil {
			t.Fatal(err)
		}
		return RunResultCallbackRequest{Status: "SUCCEEDED"}
	}, func(int64, RunResultCallbackRequest) error { calls++; return nil }, wrapper)
	if err != nil || executions != 1 || calls != 1 || failures != 1 {
		t.Fatal(err, executions, calls, failures)
	}
}

func TestStoreLimitsCleanupAndCorruption(t *testing.T) {
	s := testStore(t)
	now := time.Now()
	old := now.Add(-resultRetention)
	recent := now.Add(-time.Hour)
	for id := int64(1); id <= 3; id++ {
		r := fixtureRecord(id)
		if id == 2 {
			r.DeliveredAt = &old
		}
		if id == 3 {
			r.DeliveredAt = &recent
		}
		if err := s.save(r); err != nil {
			t.Fatal(err)
		}
	}
	if err := s.admit(now); err != nil {
		t.Fatal(err)
	}
	for id := int64(1); id <= 3; id++ {
		r, err := s.load(id)
		if err != nil || (r == nil) != (id == 2) {
			t.Fatal(id, r, err)
		}
	}
	r := fixtureRecord(4)
	r.Result.StdoutPreview = strings.Repeat("x", resultRecordLimit)
	if err := s.save(r); err == nil {
		t.Fatal("oversized result accepted")
	}
	if err := os.WriteFile(s.path(5), []byte("broken"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := openResultStore(s.dir); err == nil {
		t.Fatal("corrupt store accepted")
	}
}

func TestFullStoreStopsBeforeExecution(t *testing.T) {
	s := testStore(t)
	for id := int64(1); id <= resultEntryLimit; id++ {
		r := fixtureRecord(id)
		data, _ := json.Marshal(r)
		if err := os.WriteFile(s.path(id), data, 0600); err != nil {
			t.Fatal(err)
		}
	}
	err := s.process(RunRequestedEvent{JobID: resultEntryLimit + 1}, func() error { t.Fatal("started with full store"); return nil }, func(RunRequestedEvent) RunResultCallbackRequest {
		t.Fatal("executed with full store")
		return RunResultCallbackRequest{}
	}, nil, boundedRetry(t))
	if err == nil {
		t.Fatal("full store admitted work")
	}
}

func TestRetryDelayIsBounded(t *testing.T) {
	attempts := 0
	var delays []time.Duration
	retryResult(func() error {
		attempts++
		if attempts < 10 {
			return errors.New("offline")
		}
		return nil
	}, func(d time.Duration) { delays = append(delays, d) })
	if len(delays) != 9 || delays[0] != time.Second || delays[8] != time.Minute {
		t.Fatal(delays)
	}
}

func TestWalkDoesNotRetainAllPayloads(t *testing.T) {
	s := testStore(t)
	for id := int64(1); id <= 128; id++ {
		record := fixtureRecord(id)
		record.Result.StdoutPreview = strings.Repeat("x", 100*1024)
		if err := s.save(record); err != nil {
			t.Fatal(err)
		}
	}
	runtime.GC()
	var baseline runtime.MemStats
	runtime.ReadMemStats(&baseline)
	visited := 0
	if err := s.walk(func(record *completedResult) error {
		visited++
		if len(record.Result.StdoutPreview) != 100*1024 {
			t.Fatal("missing payload")
		}
		if visited%32 == 0 {
			runtime.GC()
			var current runtime.MemStats
			runtime.ReadMemStats(&current)
			if current.HeapAlloc > baseline.HeapAlloc+8*1024*1024 {
				t.Fatal("walk retained multiple megabytes of completed payloads")
			}
		}
		return nil
	}); err != nil {
		t.Fatal(err)
	}
	if visited != 128 {
		t.Fatal(visited)
	}
	if _, err := openResultStore(s.dir); err != nil {
		t.Fatal(err)
	}
	if err := s.admit(time.Now()); err != nil {
		t.Fatal(err)
	}
}

func TestDeliveryMarkerFailureRetriesOnlyPersistence(t *testing.T) {
	s := testStore(t)
	record := fixtureRecord(1)
	if err := s.save(record); err != nil {
		t.Fatal(err)
	}
	calls := 0
	send := func(int64, RunResultCallbackRequest) error {
		calls++
		if err := os.Remove(s.path(1)); err != nil {
			t.Fatal(err)
		}
		if err := os.Mkdir(s.path(1), 0700); err != nil {
			t.Fatal(err)
		}
		return nil
	}
	operations := 0
	retry := func(operation func() error) {
		operations++
		if operations == 2 {
			if err := operation(); err == nil {
				t.Fatal("expected marker failure")
			}
			if err := os.Remove(s.path(1)); err != nil {
				t.Fatal(err)
			}
		}
		boundedRetry(t)(operation)
	}
	s.deliver(&record, send, retry)
	saved, err := s.load(1)
	if err != nil || saved == nil || saved.DeliveredAt == nil || calls != 1 {
		t.Fatal(saved, err, calls)
	}
}

func TestHTTPCallbackFailureThenRecovery(t *testing.T) {
	calls, executions := 0, 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.URL.Path != "/api/internal/runs/7/result" || r.Header.Get("X-Worker-Token") != "test-token" {
			t.Error("callback contract changed")
		}
		var result RunResultCallbackRequest
		if err := json.NewDecoder(r.Body).Decode(&result); err != nil || result.Status != "SUCCEEDED" {
			t.Error("invalid result", err)
		}
		if calls == 1 {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()
	s := testStore(t)
	err := s.process(RunRequestedEvent{JobID: 7}, func() error { return nil }, func(RunRequestedEvent) RunResultCallbackRequest {
		executions++
		return RunResultCallbackRequest{Status: "SUCCEEDED"}
	}, func(id int64, result RunResultCallbackRequest) error {
		return sendResult(server.URL, "test-token", id, result)
	}, boundedRetry(t))
	if err != nil || calls != 2 || executions != 1 {
		t.Fatal(err, calls, executions)
	}
}
