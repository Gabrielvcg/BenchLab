package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"hash"
	"strconv"
	"strings"
)

// Previews are bounded; identity and byte counts cover every user byte.
type boundedCapture struct {
	buffer    bytes.Buffer
	limit     int
	total     int64
	truncated bool
	digest    hash.Hash
}

func newBoundedCapture(limit int) boundedCapture {
	return boundedCapture{limit: max(limit, 0), digest: sha256.New()}
}

func (c *boundedCapture) Write(p []byte) (int, error) {
	c.total += int64(len(p))
	_, _ = c.digest.Write(p)
	if remaining := c.limit - c.buffer.Len(); remaining > 0 {
		_, _ = c.buffer.Write(p[:min(len(p), remaining)])
	}
	c.truncated = c.total > int64(c.limit)
	return len(p), nil
}
func (c *boundedCapture) String() string    { return c.buffer.String() }
func (c *boundedCapture) TotalBytes() int64 { return c.total }
func (c *boundedCapture) Truncated() bool   { return c.truncated }
func (c *boundedCapture) Digest() string    { return hex.EncodeToString(c.digest.Sum(nil)) }

// Explicit domain and fixed-width stream digests prevent concatenation ambiguity.
func outputChecksum(stdoutDigest, stderrDigest string) string {
	return sha256Of("benchlab-output-v1\nstdout-sha256=" + stdoutDigest + "\nstderr-sha256=" + stderrDigest + "\n")
}

const measurementTailLimit = 256
const measurementFrame = "__BENCHLAB_METRICS_V1__"

type measurementTiming struct{ wallMicros, cpuMicros int64 }
type measurementCapture struct {
	user     *boundedCapture
	token    string
	tail     []byte
	finished bool
}

func newMeasurementCapture(user *boundedCapture, token string) *measurementCapture {
	return &measurementCapture{user: user, token: token, tail: make([]byte, 0, measurementTailLimit)}
}

// Only the last bounded bytes are deferred. No append ever copies an unbounded chunk.
func (c *measurementCapture) Write(p []byte) (int, error) {
	n := len(p)
	if len(p) >= measurementTailLimit {
		_, _ = c.user.Write(c.tail)
		_, _ = c.user.Write(p[:len(p)-measurementTailLimit])
		c.tail = append(c.tail[:0], p[len(p)-measurementTailLimit:]...)
	} else {
		if excess := len(c.tail) + len(p) - measurementTailLimit; excess > 0 {
			_, _ = c.user.Write(c.tail[:excess])
			copy(c.tail, c.tail[excess:])
			c.tail = c.tail[:len(c.tail)-excess]
		}
		c.tail = append(c.tail, p...)
	}
	return n, nil
}

func validMeasurementToken(token string) bool {
	if len(token) != 32 {
		return false
	}
	for _, c := range token {
		if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
			return false
		}
	}
	return true
}
func formatMeasurementFooter(token string, wall, cpu int64) string {
	return "\n" + measurementFrame + " " + token + " " + strconv.FormatInt(wall, 10) + " " + strconv.FormatInt(cpu, 10) + "\n"
}
func parseMicroseconds(s string, allowUnavailable bool) (int64, bool) {
	if allowUnavailable && s == "-1" {
		return -1, true
	}
	if len(s) == 0 {
		return 0, false
	}
	for _, c := range s {
		if c < '0' || c > '9' {
			return 0, false
		}
	}
	value, err := strconv.ParseInt(s, 10, 64)
	return value, err == nil
}

// Only the final complete line, bound to this invocation, can be the helper frame.
// Earlier marker-like user lines remain byte-for-byte user data, even if duplicated.
func (c *measurementCapture) finish() (measurementTiming, bool) {
	if c.finished {
		return measurementTiming{}, false
	}
	c.finished = true
	flush := func() (measurementTiming, bool) {
		_, _ = c.user.Write(c.tail)
		c.tail = nil
		return measurementTiming{}, false
	}
	if !validMeasurementToken(c.token) || len(c.tail) == 0 || c.tail[len(c.tail)-1] != '\n' {
		return flush()
	}
	separator := bytes.LastIndexByte(c.tail[:len(c.tail)-1], '\n')
	if separator < 0 {
		return flush()
	}
	fields := strings.Split(string(c.tail[separator+1:len(c.tail)-1]), " ")
	if len(fields) != 4 || fields[0] != measurementFrame || fields[1] != c.token {
		return flush()
	}
	wall, ok := parseMicroseconds(fields[2], false)
	if !ok {
		return flush()
	}
	cpu, ok := parseMicroseconds(fields[3], true)
	if !ok {
		return flush()
	}
	_, _ = c.user.Write(c.tail[:separator])
	c.tail = nil
	return measurementTiming{wall, cpu}, true
}

func roundedMilliseconds(micros int64) int64 {
	// Avoid overflowing micros+999 for a malformed-but-parseable large measurement.
	result := micros / 1000
	if micros%1000 != 0 {
		result++
	}
	return result
}
