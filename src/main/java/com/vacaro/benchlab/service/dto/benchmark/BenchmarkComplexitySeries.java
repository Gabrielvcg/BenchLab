package com.vacaro.benchlab.service.dto.benchmark;

import java.util.List;

public record BenchmarkComplexitySeries(String language, List<BenchmarkComplexityPoint> points) {}
