package com.vacaro.benchlab.service.dto.benchmark;

import java.util.List;

public record BenchmarkTimeseriesResponse(Long algorithmId, String language, List<BenchmarkTimeseriesPoint> points) {}
