package com.vacaro.benchlab.service.dto.benchmark;

public record BenchmarkCompareRow(String language, Double avg, Double stddev, Long p50, Long p95, Integer validSamples) {}
