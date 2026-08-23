package com.vacaro.benchlab.service.dto.benchmark;

public record RunMetricResponse(
    Long cpuTimeMs,
    Long orchestrationWallTimeMs,
    Double peakMemoryMb,
    Integer exitCode,
    Boolean timedOut,
    Long compileWallTimeMs
) {}
