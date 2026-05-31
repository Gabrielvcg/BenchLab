package com.vacaro.benchlab.service.dto.benchmark;

public record RunArtifactResponse(String stdoutTruncated, String stderrTruncated, Long outputSizeBytes, String artifactChecksum, String technicalLogSummary) {}
