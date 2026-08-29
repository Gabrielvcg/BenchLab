package com.vacaro.benchlab.service.dto.benchmark;

public record RunArtifactResponse(
    String stdoutPreview,
    String stderrPreview,
    Boolean stdoutTruncated,
    Boolean stderrTruncated,
    Long outputSizeBytes,
    String artifactChecksum,
    String technicalLogSummary
) {}
