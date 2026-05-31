package com.vacaro.benchlab.service.dto.benchmark;

public record RunMetricResponse(Long cpuTimeMs, Long wallTimeMs, Double peakMemoryMb, Integer exitCode, Boolean timedOut, Long compileMs) {}
