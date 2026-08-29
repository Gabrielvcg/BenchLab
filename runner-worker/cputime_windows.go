//go:build windows

package main

func childCPUTimeMicros() (int64, bool) {
	return 0, false
}
