package com.vacaro.benchlab.service.dto.benchmark;

public record BenchmarkComplexityPoint(
    Long datasetId,
    Long datasetSize,
    Double avg,
    Double stddev,
    Long p50,
    Long p95,
    Integer validSamples
) {}
