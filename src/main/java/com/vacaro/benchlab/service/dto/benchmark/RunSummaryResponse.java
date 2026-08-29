package com.vacaro.benchlab.service.dto.benchmark;

public record RunSummaryResponse(
    Long id,
    String status,
    String language,
    Long algorithmId,
    String algorithmName,
    Long datasetId,
    Long datasetSize,
    String queuedAt,
    String finishedAt,
    Long cpuTimeMs,
    Long orchestrationWallTimeMs,
    Long executionWallTimeMs
) {}
