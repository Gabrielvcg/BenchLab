package com.vacaro.benchlab.service.dto.benchmark;

import com.vacaro.benchlab.domain.BenchmarkRunStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RunResultCallbackRequest(
    @NotNull BenchmarkRunStatus status,
    @NotBlank @Size(max = 255) String runnerHost,
    @Size(max = 2000) String failureReason,
    @PositiveOrZero Long cpuTimeMs,
    @NotNull @PositiveOrZero Long orchestrationWallTimeMs,
    @PositiveOrZero Long executionWallTimeMs,
    @PositiveOrZero Double peakMemoryMb,
    @NotNull Integer exitCode,
    @NotNull Boolean timedOut,
    @NotNull @PositiveOrZero Long compileWallTimeMs,
    @Size(max = 8000) String stdoutPreview,
    @Size(max = 8000) String stderrPreview,
    @NotNull Boolean stdoutTruncated,
    @NotNull Boolean stderrTruncated,
    @NotNull @PositiveOrZero Long outputSizeBytes,
    @Size(max = 255) String artifactChecksum,
    @Size(max = 2000) String technicalLogSummary
) {}
