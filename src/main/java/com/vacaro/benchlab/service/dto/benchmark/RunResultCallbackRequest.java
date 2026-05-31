package com.vacaro.benchlab.service.dto.benchmark;

public record RunResultCallbackRequest(
    String status,
    String runnerHost,
    String failureReason,
    Long cpuTimeMs,
    Long wallTimeMs,
    Double peakMemoryMb,
    Integer exitCode,
    Boolean timedOut,
    Long compileMs,
    String stdoutTruncated,
    String stderrTruncated,
    Long outputSizeBytes,
    String artifactChecksum,
    String technicalLogSummary
) {}
