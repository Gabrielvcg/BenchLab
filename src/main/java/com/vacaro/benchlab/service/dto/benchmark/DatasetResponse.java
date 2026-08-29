package com.vacaro.benchlab.service.dto.benchmark;

public record DatasetResponse(Long id, String type, Long sizeValue, Long seed, String checksum, String datasetVersion) {}
