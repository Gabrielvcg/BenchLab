package com.vacaro.benchlab.service.dto.benchmark;

public record BenchmarkComplexityPoint(
    Long datasetId,
    Long datasetSize,
    Double avg,
    Double stddev,
    Double p50,
    Double p95,
    Integer validSamples
) {}
