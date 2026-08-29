package com.vacaro.benchlab.service.dto.benchmark;

import java.util.List;

public record BenchmarkCompareResponse(Long algorithmId, Long datasetId, List<BenchmarkCompareRow> rows) {}
