package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const resultRecordLimit = 128 * 1024
const resultEntryLimit = 10000
const resultRetention = 7 * 24 * time.Hour

type completedResult struct {
	Version     int                      `json:"version"`
	JobID       int64                    `json:"jobId"`
	EventHash   string                   `json:"eventHash"`
	Result      RunResultCallbackRequest `json:"result"`
	DeliveredAt *time.Time               `json:"deliveredAt,omitempty"`
}

// One process owns this directory. Pending results never expire.
type resultStore struct{ dir string }

func openResultStore(dir string) (*resultStore, error) {
	if err := os.MkdirAll(dir, 0700); err != nil {
		return nil, err
	}
	s := &resultStore{dir: dir}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), ".result-") {
			if err := os.Remove(filepath.Join(dir, entry.Name())); err != nil {
				return nil, err
			}
		}
	}
	err = s.walk(nil)
	return s, err
}

func (s *resultStore) path(id int64) string {
	return filepath.Join(s.dir, strconv.FormatInt(id, 10)+".json")
}

func (s *resultStore) load(id int64) (*completedResult, error) {
	info, err := os.Stat(s.path(id))
	if os.IsNotExist(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if info.Size() > resultRecordLimit {
		return nil, fmt.Errorf("result %d exceeds size limit", id)
	}
	data, err := os.ReadFile(s.path(id))
	if err != nil {
		return nil, err
	}
	var record completedResult
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	if record.Version != 1 || record.JobID != id || id <= 0 || record.Result.Status == "" || record.EventHash == "" {
		return nil, fmt.Errorf("invalid result record %d", id)
	}
	return &record, nil
}

// walk retains filenames and at most one decoded payload, never all result bodies.
func (s *resultStore) walk(visit func(*completedResult) error) error {
	entries, err := os.ReadDir(s.dir)
	if err != nil {
		return err
	}
	count := 0
	for _, entry := range entries {
		if !strings.HasSuffix(entry.Name(), ".json") {
			continue
		}
		count++
		if count > resultEntryLimit {
			return fmt.Errorf("result store exceeds capacity")
		}
		id, err := strconv.ParseInt(strings.TrimSuffix(entry.Name(), ".json"), 10, 64)
		if err != nil {
			return err
		}
		record, err := s.load(id)
		if err != nil {
			return err
		}
		if record == nil {
			return fmt.Errorf("missing result %d", id)
		}
		if visit != nil {
			if err := visit(record); err != nil {
				return err
			}
		}
	}
	return nil
}

func (s *resultStore) save(record completedResult) error {
	data, err := json.Marshal(record)
	if err != nil {
		return err
	}
	if len(data) > resultRecordLimit {
		return fmt.Errorf("result exceeds size limit")
	}
	file, err := os.CreateTemp(s.dir, ".result-")
	if err != nil {
		return err
	}
	defer os.Remove(file.Name())
	if _, err := file.Write(data); err != nil {
		file.Close()
		return err
	}
	if err := file.Sync(); err != nil {
		file.Close()
		return err
	}
	if err := file.Close(); err != nil {
		return err
	}
	if err := os.Rename(file.Name(), s.path(record.JobID)); err != nil {
		return err
	}
	return syncResultDirectory(s.dir)
}

func (s *resultStore) admit(now time.Time) error {
	count := 0
	err := s.walk(func(record *completedResult) error {
		if record.DeliveredAt != nil && now.Sub(*record.DeliveredAt) >= resultRetention {
			return os.Remove(s.path(record.JobID))
		}
		count++
		return nil
	})
	if err != nil {
		return err
	}

	if err := syncResultDirectory(s.dir); err != nil {
		return err
	}
	if count >= resultEntryLimit {
		return fmt.Errorf("result store full; waiting for delivered retention to expire")
	}
	return nil
}

// Retry the same in-memory operation: persistence failure must not restart execution.
func retryResult(operation func() error, wait func(time.Duration)) {
	delay := time.Second
	for {
		if err := operation(); err == nil {
			return
		}
		wait(delay)
		delay = min(delay*2, time.Minute)
	}
}

func (s *resultStore) deliver(record *completedResult, send func(int64, RunResultCallbackRequest) error, retry func(func() error)) {
	if record.DeliveredAt != nil {
		return
	}
	retry(func() error { return send(record.JobID, record.Result) })
	now := time.Now().UTC()
	record.DeliveredAt = &now
	retry(func() error { return s.save(*record) })
}

func (s *resultStore) process(event RunRequestedEvent, start func() error, execute func(RunRequestedEvent) RunResultCallbackRequest, send func(int64, RunResultCallbackRequest) error, retry func(func() error)) error {
	if event.JobID <= 0 {
		return fmt.Errorf("invalid job ID")
	}
	body, err := json.Marshal(event)
	if err != nil {
		return err
	}
	fingerprint := sha256Of(string(body))
	record, err := s.load(event.JobID)
	if err != nil {
		return err
	}
	if record != nil && record.EventHash != fingerprint {
		return fmt.Errorf("job ID reused with different event")
	}
	if record == nil {
		if err := s.admit(time.Now()); err != nil {
			return err
		}
		if err := start(); err != nil {
			return err
		}
		record = &completedResult{Version: 1, JobID: event.JobID, EventHash: fingerprint, Result: execute(event)}
		retry(func() error { return s.save(*record) })
	}
	s.deliver(record, send, retry)
	return nil
}
