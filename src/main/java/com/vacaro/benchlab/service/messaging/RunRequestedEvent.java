package com.vacaro.benchlab.service.messaging;

public record RunRequestedEvent(
    Long jobId,
    Long implementationId,
    Long datasetId,
    Long datasetSize,
    Long datasetSeed,
    String language,
    String sourceCode,
    String compileConfig,
    String runtimeConfig,
    String datasetVersion,
    Integer timeoutMs,
    Integer memoryMb,
    Double cpuLimit,
    Integer iterations,
    String traceId
) {}
