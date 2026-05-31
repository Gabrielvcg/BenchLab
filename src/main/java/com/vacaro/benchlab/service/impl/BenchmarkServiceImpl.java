package com.vacaro.benchlab.service.impl;

import com.vacaro.benchlab.domain.Algorithm;
import com.vacaro.benchlab.domain.BenchmarkRun;
import com.vacaro.benchlab.domain.BenchmarkRunStatus;
import com.vacaro.benchlab.domain.Dataset;
import com.vacaro.benchlab.domain.Implementation;
import com.vacaro.benchlab.domain.RunArtifact;
import com.vacaro.benchlab.domain.RunMetric;
import com.vacaro.benchlab.repository.AlgorithmRepository;
import com.vacaro.benchlab.repository.BenchmarkRunRepository;
import com.vacaro.benchlab.repository.DatasetRepository;
import com.vacaro.benchlab.repository.ImplementationRepository;
import com.vacaro.benchlab.repository.RunArtifactRepository;
import com.vacaro.benchlab.repository.RunMetricRepository;
import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.dto.benchmark.AlgorithmResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkCompareResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkCompareRow;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkTimeseriesPoint;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkTimeseriesResponse;
import com.vacaro.benchlab.service.dto.benchmark.CreateAlgorithmRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateImplementationRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateRunRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateDatasetRequest;
import com.vacaro.benchlab.service.dto.benchmark.DatasetResponse;
import com.vacaro.benchlab.service.dto.benchmark.ImplementationResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunArtifactResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunMetricResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResultCallbackRequest;
import com.vacaro.benchlab.service.messaging.RunEventPublisher;
import com.vacaro.benchlab.service.messaging.RunRequestedEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BenchmarkServiceImpl implements BenchmarkService {

    private final AlgorithmRepository algorithmRepository;
    private final DatasetRepository datasetRepository;
    private final ImplementationRepository implementationRepository;
    private final BenchmarkRunRepository benchmarkRunRepository;
    private final RunMetricRepository runMetricRepository;
    private final RunArtifactRepository runArtifactRepository;
    private final RunEventPublisher runEventPublisher;

    public BenchmarkServiceImpl(
        AlgorithmRepository algorithmRepository,
        DatasetRepository datasetRepository,
        ImplementationRepository implementationRepository,
        BenchmarkRunRepository benchmarkRunRepository,
        RunMetricRepository runMetricRepository,
        RunArtifactRepository runArtifactRepository,
        RunEventPublisher runEventPublisher
    ) {
        this.algorithmRepository = algorithmRepository;
        this.datasetRepository = datasetRepository;
        this.implementationRepository = implementationRepository;
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.runMetricRepository = runMetricRepository;
        this.runArtifactRepository = runArtifactRepository;
        this.runEventPublisher = runEventPublisher;
    }

    @Override
    public AlgorithmResponse createAlgorithm(CreateAlgorithmRequest request) {
        Algorithm algorithm = new Algorithm();
        algorithm.setName(request.name());
        algorithm.setCategory(request.category());
        algorithm.setVersion(request.version());
        algorithm.setComplexityDeclared(request.complexityDeclared());
        Algorithm saved = algorithmRepository.save(algorithm);
        return new AlgorithmResponse(
            saved.getId(),
            saved.getName(),
            saved.getCategory(),
            saved.getVersion(),
            saved.getComplexityDeclared()
        );
    }

    @Override
    public DatasetResponse createDataset(CreateDatasetRequest request) {
        Dataset dataset = new Dataset();
        dataset.setType(request.type());
        dataset.setSizeValue(request.sizeValue());
        dataset.setSeed(request.seed());
        dataset.setChecksum(request.checksum());
        dataset.setDatasetVersion(request.datasetVersion());
        Dataset saved = datasetRepository.save(dataset);
        return new DatasetResponse(saved.getId(), saved.getType(), saved.getSizeValue(), saved.getSeed(), saved.getChecksum(), saved.getDatasetVersion());
    }

    @Override
    public ImplementationResponse createImplementation(CreateImplementationRequest request) {
        Algorithm algorithm = algorithmRepository
            .findById(request.algorithmId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Algorithm not found"));

        Implementation implementation = new Implementation();
        implementation.setAlgorithm(algorithm);
        implementation.setLanguage(request.language());
        implementation.setSourceCode(request.sourceCode());
        implementation.setCompileConfig(request.compileConfig());
        implementation.setRuntimeConfig(request.runtimeConfig());
        implementation.setImplementationHash(sha256(request.sourceCode()));

        Implementation saved = implementationRepository.save(implementation);
        return new ImplementationResponse(saved.getId(), algorithm.getId(), saved.getLanguage().name(), saved.getImplementationHash());
    }

    @Override
    public RunResponse createRun(CreateRunRequest request) {
        Implementation implementation = implementationRepository
            .findById(request.implementationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Implementation not found"));
        Dataset dataset = datasetRepository
            .findById(request.datasetId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found"));

        BenchmarkRun run = new BenchmarkRun();
        run.setImplementation(implementation);
        run.setDataset(dataset);
        run.setStatus(BenchmarkRunStatus.QUEUED);
        run.setTraceId(UUID.randomUUID().toString());
        run.setQueuedAt(Instant.now());
        BenchmarkRun saved = benchmarkRunRepository.save(run);

        runEventPublisher.publish(
            new RunRequestedEvent(
                saved.getId(),
                implementation.getId(),
                dataset.getId(),
                implementation.getLanguage().name(),
                implementation.getSourceCode(),
                implementation.getCompileConfig(),
                implementation.getRuntimeConfig(),
                dataset.getDatasetVersion(),
                request.timeoutMs() == null ? 5000 : request.timeoutMs(),
                request.memoryMb() == null ? 256 : request.memoryMb(),
                request.cpuLimit() == null ? 1.0 : request.cpuLimit(),
                request.iterations() == null ? 5 : request.iterations(),
                saved.getTraceId()
            )
        );

        return toRunResponse(saved, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public RunResponse getRun(Long runId) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        RunMetric metric = runMetricRepository.findByBenchmarkRunId(runId).orElse(null);
        RunArtifact artifact = runArtifactRepository.findByBenchmarkRunId(runId).orElse(null);
        return toRunResponse(run, metric, artifact);
    }

    @Override
    public void registerRunResult(Long runId, RunResultCallbackRequest request) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        run.setStatus(BenchmarkRunStatus.valueOf(request.status()));
        run.setRunnerHost(request.runnerHost());
        run.setFailureReason(request.failureReason());
        if (run.getStartedAt() == null) {
            run.setStartedAt(Instant.now());
        }
        run.setFinishedAt(Instant.now());
        benchmarkRunRepository.save(run);

        RunMetric metric = runMetricRepository.findByBenchmarkRunId(runId).orElseGet(RunMetric::new);
        metric.setBenchmarkRun(run);
        metric.setCpuTimeMs(request.cpuTimeMs());
        metric.setWallTimeMs(request.wallTimeMs());
        metric.setPeakMemoryMb(request.peakMemoryMb());
        metric.setExitCode(request.exitCode());
        metric.setTimedOut(request.timedOut());
        metric.setCompileMs(request.compileMs());
        runMetricRepository.save(metric);

        RunArtifact artifact = runArtifactRepository.findByBenchmarkRunId(runId).orElseGet(RunArtifact::new);
        artifact.setBenchmarkRun(run);
        artifact.setStdoutTruncated(request.stdoutTruncated());
        artifact.setStderrTruncated(request.stderrTruncated());
        artifact.setOutputSizeBytes(request.outputSizeBytes());
        artifact.setArtifactChecksum(request.artifactChecksum());
        artifact.setTechnicalLogSummary(request.technicalLogSummary());
        runArtifactRepository.save(artifact);
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkCompareResponse compare(Long algorithmId, Long datasetId) {
        List<BenchmarkRun> runs = benchmarkRunRepository.findByImplementationAlgorithmIdAndDatasetId(algorithmId, datasetId);
        var grouped = runs
            .stream()
            .filter(r -> r.getStatus() == BenchmarkRunStatus.SUCCEEDED)
            .collect(java.util.stream.Collectors.groupingBy(r -> r.getImplementation().getLanguage().name()));

        List<BenchmarkCompareRow> rows = new ArrayList<>();
        grouped.forEach((language, languageRuns) -> {
            List<Long> values = languageRuns
                .stream()
                .map(r -> runMetricRepository.findByBenchmarkRunId(r.getId()).orElse(null))
                .filter(m -> m != null && m.getWallTimeMs() != null)
                .map(RunMetric::getWallTimeMs)
                .sorted()
                .toList();
            if (values.isEmpty()) {
                return;
            }
            double avg = values.stream().mapToLong(Long::longValue).average().orElse(0);
            double stddev = calcStdDev(values, avg);
            long p50 = percentile(values, 50);
            long p95 = percentile(values, 95);
            rows.add(new BenchmarkCompareRow(language, avg, stddev, p50, p95, values.size()));
        });

        rows.sort(Comparator.comparing(BenchmarkCompareRow::avg));
        return new BenchmarkCompareResponse(algorithmId, datasetId, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkTimeseriesResponse timeseries(Long algorithmId, String language) {
        List<BenchmarkTimeseriesPoint> points = benchmarkRunRepository
            .findAll()
            .stream()
            .filter(r -> r.getImplementation().getAlgorithm().getId().equals(algorithmId))
            .filter(r -> r.getImplementation().getLanguage().name().equalsIgnoreCase(language))
            .filter(r -> r.getFinishedAt() != null)
            .map(r -> {
                RunMetric metric = runMetricRepository.findByBenchmarkRunId(r.getId()).orElse(null);
                if (metric == null || metric.getWallTimeMs() == null) {
                    return null;
                }
                return new BenchmarkTimeseriesPoint(r.getFinishedAt().toString(), metric.getWallTimeMs());
            })
            .filter(p -> p != null)
            .sorted(Comparator.comparing(BenchmarkTimeseriesPoint::finishedAt))
            .toList();

        return new BenchmarkTimeseriesResponse(algorithmId, language, points);
    }

    private static RunResponse toRunResponse(BenchmarkRun run, RunMetric metric, RunArtifact artifact) {
        RunMetricResponse metricResponse = metric == null
            ? null
            : new RunMetricResponse(
                metric.getCpuTimeMs(),
                metric.getWallTimeMs(),
                metric.getPeakMemoryMb(),
                metric.getExitCode(),
                metric.getTimedOut(),
                metric.getCompileMs()
            );

        RunArtifactResponse artifactResponse = artifact == null
            ? null
            : new RunArtifactResponse(
                artifact.getStdoutTruncated(),
                artifact.getStderrTruncated(),
                artifact.getOutputSizeBytes(),
                artifact.getArtifactChecksum(),
                artifact.getTechnicalLogSummary()
            );

        return new RunResponse(
            run.getId(),
            run.getStatus().name(),
            run.getTraceId(),
            run.getQueuedAt() == null ? null : run.getQueuedAt().toString(),
            run.getStartedAt() == null ? null : run.getStartedAt().toString(),
            run.getFinishedAt() == null ? null : run.getFinishedAt().toString(),
            run.getFailureReason(),
            metricResponse,
            artifactResponse
        );
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static double calcStdDev(List<Long> values, double avg) {
        double variance = values.stream().mapToDouble(v -> Math.pow(v - avg, 2)).average().orElse(0.0);
        return Math.sqrt(variance);
    }

    private static long percentile(List<Long> sortedValues, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        int safeIndex = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(safeIndex);
    }
}
