package com.vacaro.benchlab.service.dto.benchmark;

public record RunResponse(Long id, String status, String traceId, String queuedAt, String startedAt, String finishedAt, String failureReason, RunMetricResponse metric, RunArtifactResponse artifact) {}
