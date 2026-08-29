//go:build !windows

package main

import "syscall"

func childCPUTimeMicros() (int64, bool) {
	var usage syscall.Rusage
	if err := syscall.Getrusage(syscall.RUSAGE_CHILDREN, &usage); err != nil {
		return 0, false
	}
	return timevalMicros(usage.Utime) + timevalMicros(usage.Stime), true
}

func timevalMicros(value syscall.Timeval) int64 {
	return value.Sec*1_000_000 + value.Usec
}
