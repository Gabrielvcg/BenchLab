package com.vacaro.benchlab.repository;

import com.vacaro.benchlab.domain.BenchmarkRun;
import com.vacaro.benchlab.domain.BenchmarkRunStatus;
import com.vacaro.benchlab.domain.ImplementationLanguage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
    Optional<BenchmarkRun> findByTraceId(String traceId);

    long countByRequestedByAndStatusIn(String requestedBy, Collection<BenchmarkRunStatus> statuses);

    @EntityGraph(attributePaths = { "implementation", "implementation.algorithm", "dataset" })
    List<BenchmarkRun> findTop25ByOrderByQueuedAtDesc();

    @Query(
        """
        select
            implementation.language as language,
            dataset.id as datasetId,
            dataset.sizeValue as datasetSize,
            run.finishedAt as finishedAt,
            metric.orchestrationWallTimeMs as orchestrationWallTimeMs,
            metric.cpuTimeMs as cpuTimeMs
        from BenchmarkRun run
        join run.implementation implementation
        join implementation.algorithm algorithm
        join run.dataset dataset
        join RunMetric metric on metric.benchmarkRun = run
        where algorithm.id = :algorithmId
          and dataset.id = :datasetId
          and run.status = :status
        """
    )
    List<BenchmarkMetricSample> findComparisonSamples(
        @Param("algorithmId") Long algorithmId,
        @Param("datasetId") Long datasetId,
        @Param("status") BenchmarkRunStatus status
    );

    @Query(
        """
        select
            implementation.language as language,
            dataset.id as datasetId,
            dataset.sizeValue as datasetSize,
            run.finishedAt as finishedAt,
            metric.orchestrationWallTimeMs as orchestrationWallTimeMs,
            metric.cpuTimeMs as cpuTimeMs
        from BenchmarkRun run
        join run.implementation implementation
        join implementation.algorithm algorithm
        join run.dataset dataset
        join RunMetric metric on metric.benchmarkRun = run
        where algorithm.id = :algorithmId
          and implementation.language = :language
          and run.status = :status
          and run.finishedAt is not null
        order by run.finishedAt
        """
    )
    List<BenchmarkMetricSample> findTimeseriesSamples(
        @Param("algorithmId") Long algorithmId,
        @Param("language") ImplementationLanguage language,
        @Param("status") BenchmarkRunStatus status
    );

    @Query(
        """
        select
            implementation.language as language,
            dataset.id as datasetId,
            dataset.sizeValue as datasetSize,
            run.finishedAt as finishedAt,
            metric.orchestrationWallTimeMs as orchestrationWallTimeMs,
            metric.cpuTimeMs as cpuTimeMs
        from BenchmarkRun run
        join run.implementation implementation
        join implementation.algorithm algorithm
        join run.dataset dataset
        join RunMetric metric on metric.benchmarkRun = run
        where algorithm.id = :algorithmId
          and run.status = :status
        """
    )
    List<BenchmarkMetricSample> findComplexitySamples(
        @Param("algorithmId") Long algorithmId,
        @Param("status") BenchmarkRunStatus status
    );
}
