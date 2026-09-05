package main

import (
	"os"
	"strconv"
)

// Compilation is not an algorithm sample and needs an independent bounded budget.
func compilationTimeoutMs() int {
	value, err := strconv.Atoi(os.Getenv("WORKER_COMPILE_TIMEOUT_MS"))
	if err != nil || value < 1000 || value > 300000 {
		return 120000
	}
	return value
}
