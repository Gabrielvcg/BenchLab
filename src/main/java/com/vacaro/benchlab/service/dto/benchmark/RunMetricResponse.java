package com.vacaro.benchlab.service.dto.benchmark;

public record RunMetricResponse(
    Long cpuTimeMs,
    Long orchestrationWallTimeMs,
    Long executionWallTimeMs,
    Double peakMemoryMb,
    Integer exitCode,
    Boolean timedOut,
    Long compileWallTimeMs
) {}
