package com.vacaro.benchlab.service.dto.benchmark;

public record BenchmarkTimeseriesPoint(String finishedAt, Long orchestrationWallTimeMs) {}
