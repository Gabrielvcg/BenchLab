package com.vacaro.benchlab.repository;

import com.vacaro.benchlab.domain.RunMetric;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunMetricRepository extends JpaRepository<RunMetric, Long> {
    Optional<RunMetric> findByBenchmarkRunId(Long benchmarkRunId);
    List<RunMetric> findByBenchmarkRunIdIn(Collection<Long> benchmarkRunIds);
}
