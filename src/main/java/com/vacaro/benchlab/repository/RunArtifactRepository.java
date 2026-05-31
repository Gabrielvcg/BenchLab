package com.vacaro.benchlab.repository;

import com.vacaro.benchlab.domain.RunArtifact;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunArtifactRepository extends JpaRepository<RunArtifact, Long> {
    Optional<RunArtifact> findByBenchmarkRunId(Long benchmarkRunId);
}
