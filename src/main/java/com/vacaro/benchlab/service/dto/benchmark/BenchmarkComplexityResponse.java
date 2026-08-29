package com.vacaro.benchlab.service.dto.benchmark;

import java.util.List;

public record BenchmarkComplexityResponse(Long algorithmId, String metric, List<BenchmarkComplexitySeries> series) {}
