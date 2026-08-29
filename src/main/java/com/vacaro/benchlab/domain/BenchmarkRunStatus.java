package com.vacaro.benchlab.domain;

public enum BenchmarkRunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    COMPILE_ERROR,
    RUNTIME_ERROR,
}
