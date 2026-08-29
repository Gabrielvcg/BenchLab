package com.vacaro.benchlab.service.dto.benchmark;

public record CreateDatasetRequest(String type, Long sizeValue, Long seed, String checksum, String datasetVersion) {}
