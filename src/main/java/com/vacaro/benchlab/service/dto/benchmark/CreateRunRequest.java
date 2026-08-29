package com.vacaro.benchlab.service.dto.benchmark;

public record CreateRunRequest(
    Long implementationId,
    Long datasetId,
    Integer timeoutMs,
    Integer memoryMb,
    Double cpuLimit,
    Integer iterations,
    Integer warmupIterations
) {}
