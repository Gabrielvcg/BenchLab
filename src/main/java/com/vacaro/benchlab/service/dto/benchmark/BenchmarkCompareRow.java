package com.vacaro.benchlab.service.dto.benchmark;

public record BenchmarkCompareRow(String language, Double avg, Double stddev, Double p50, Double p95, Integer validSamples) {}
