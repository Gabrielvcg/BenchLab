package com.vacaro.benchlab.repository;

import com.vacaro.benchlab.domain.ImplementationLanguage;
import java.time.Instant;

public interface BenchmarkMetricSample {
    ImplementationLanguage getLanguage();

    Long getDatasetId();

    Long getDatasetSize();

    Instant getFinishedAt();

    Long getOrchestrationWallTimeMs();

    Long getExecutionWallTimeMs();

    Long getCpuTimeMs();
}
