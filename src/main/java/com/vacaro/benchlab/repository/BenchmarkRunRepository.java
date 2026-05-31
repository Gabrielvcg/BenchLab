package com.vacaro.benchlab.repository;

import com.vacaro.benchlab.domain.BenchmarkRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
    Optional<BenchmarkRun> findByTraceId(String traceId);
    List<BenchmarkRun> findByImplementationAlgorithmIdAndDatasetId(Long algorithmId, Long datasetId);
    List<BenchmarkRun> findTop25ByOrderByQueuedAtDesc();
}
