package com.vacaro.benchlab.service.impl;

import com.vacaro.benchlab.config.BenchLabProperties;
import com.vacaro.benchlab.domain.Algorithm;
import com.vacaro.benchlab.domain.BenchmarkRun;
import com.vacaro.benchlab.domain.BenchmarkRunStatus;
import com.vacaro.benchlab.domain.Dataset;
import com.vacaro.benchlab.domain.Implementation;
import com.vacaro.benchlab.domain.ImplementationLanguage;
import com.vacaro.benchlab.domain.RunArtifact;
import com.vacaro.benchlab.domain.RunMetric;
import com.vacaro.benchlab.repository.AlgorithmRepository;
import com.vacaro.benchlab.repository.BenchmarkMetricSample;
import com.vacaro.benchlab.repository.BenchmarkRunRepository;
import com.vacaro.benchlab.repository.DatasetRepository;
import com.vacaro.benchlab.repository.ImplementationRepository;
import com.vacaro.benchlab.repository.RunArtifactRepository;
import com.vacaro.benchlab.repository.RunMetricRepository;
import com.vacaro.benchlab.repository.UserRepository;
import com.vacaro.benchlab.security.SecurityUtils;
import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.dto.benchmark.AlgorithmResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkCompareResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkCompareRow;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkComplexityPoint;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkComplexityResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkComplexitySeries;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkTimeseriesPoint;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkTimeseriesResponse;
import com.vacaro.benchlab.service.dto.benchmark.CreateAlgorithmRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateDatasetRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateImplementationRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateRunRequest;
import com.vacaro.benchlab.service.dto.benchmark.DatasetResponse;
import com.vacaro.benchlab.service.dto.benchmark.ImplementationResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunArtifactResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunMetricResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResultCallbackRequest;
import com.vacaro.benchlab.service.dto.benchmark.RunSummaryResponse;
import com.vacaro.benchlab.service.messaging.RunEventPublisher;
import com.vacaro.benchlab.service.messaging.RunRequestedEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BenchmarkServiceImpl implements BenchmarkService {

    private static final List<BenchmarkRunStatus> OUTSTANDING_STATUSES = List.of(BenchmarkRunStatus.QUEUED, BenchmarkRunStatus.RUNNING);

    private final AlgorithmRepository algorithmRepository;
    private final DatasetRepository datasetRepository;
    private final ImplementationRepository implementationRepository;
    private final BenchmarkRunRepository benchmarkRunRepository;
    private final RunMetricRepository runMetricRepository;
    private final RunArtifactRepository runArtifactRepository;
    private final RunEventPublisher runEventPublisher;
    private final BenchLabProperties benchLabProperties;
    private final UserRepository userRepository;

    public BenchmarkServiceImpl(
        AlgorithmRepository algorithmRepository,
        DatasetRepository datasetRepository,
        ImplementationRepository implementationRepository,
        BenchmarkRunRepository benchmarkRunRepository,
        RunMetricRepository runMetricRepository,
        RunArtifactRepository runArtifactRepository,
        RunEventPublisher runEventPublisher,
        BenchLabProperties benchLabProperties,
        UserRepository userRepository
    ) {
        this.algorithmRepository = algorithmRepository;
        this.datasetRepository = datasetRepository;
        this.implementationRepository = implementationRepository;
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.runMetricRepository = runMetricRepository;
        this.runArtifactRepository = runArtifactRepository;
        this.runEventPublisher = runEventPublisher;
        this.benchLabProperties = benchLabProperties;
        this.userRepository = userRepository;
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
        validateDatasetSize(request.sizeValue());
        Dataset dataset = new Dataset();
        dataset.setType(request.type());
        dataset.setSizeValue(request.sizeValue());
        dataset.setSeed(request.seed());
        dataset.setChecksum(request.checksum());
        dataset.setDatasetVersion(request.datasetVersion());
        Dataset saved = datasetRepository.save(dataset);
        return new DatasetResponse(
            saved.getId(),
            saved.getType(),
            saved.getSizeValue(),
            saved.getSeed(),
            saved.getChecksum(),
            saved.getDatasetVersion()
        );
    }

    @Override
    public ImplementationResponse createImplementation(CreateImplementationRequest request) {
        validateSourceCode(request.sourceCode());
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
        int timeoutMs = request.timeoutMs() == null ? 5000 : request.timeoutMs();
        int memoryMb = request.memoryMb() == null ? 256 : request.memoryMb();
        double cpuLimit = request.cpuLimit() == null ? 1.0 : request.cpuLimit();
        int iterations = request.iterations() == null ? 7 : request.iterations();
        int warmupIterations = request.warmupIterations() == null ? 2 : request.warmupIterations();
        validateRunLimits(timeoutMs, memoryMb, cpuLimit, iterations, warmupIterations);

        String requestedBy = SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required"));
        userRepository
            .lockOneByLogin(requestedBy)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        long outstandingRuns = benchmarkRunRepository.countByRequestedByAndStatusIn(requestedBy, OUTSTANDING_STATUSES);
        if (outstandingRuns >= benchLabProperties.getLimits().getMaxOutstandingRunsPerUser()) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Outstanding run limit reached; wait for active runs to finish"
            );
        }

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
        run.setRequestedBy(requestedBy);
        BenchmarkRun saved = benchmarkRunRepository.save(run);

        runEventPublisher.publish(
            new RunRequestedEvent(
                saved.getId(),
                implementation.getId(),
                dataset.getId(),
                dataset.getSizeValue(),
                dataset.getSeed(),
                implementation.getLanguage().name(),
                implementation.getSourceCode(),
                implementation.getCompileConfig(),
                implementation.getRuntimeConfig(),
                dataset.getDatasetVersion(),
                timeoutMs,
                memoryMb,
                cpuLimit,
                iterations,
                warmupIterations,
                saved.getTraceId()
            )
        );

        return toRunResponse(saved, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public RunResponse getRun(Long runId) {
        BenchmarkRun run = benchmarkRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        RunMetric metric = runMetricRepository.findByBenchmarkRunId(runId).orElse(null);
        RunArtifact artifact = runArtifactRepository.findByBenchmarkRunId(runId).orElse(null);
        return toRunResponse(run, metric, artifact);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlgorithmResponse> listAlgorithms() {
        return algorithmRepository
            .findAll()
            .stream()
            .sorted(Comparator.comparing(Algorithm::getName, String.CASE_INSENSITIVE_ORDER))
            .map(algorithm ->
                new AlgorithmResponse(
                    algorithm.getId(),
                    algorithm.getName(),
                    algorithm.getCategory(),
                    algorithm.getVersion(),
                    algorithm.getComplexityDeclared()
                )
            )
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DatasetResponse> listDatasets() {
        return datasetRepository
            .findAll()
            .stream()
            .sorted(Comparator.comparing(Dataset::getSizeValue).thenComparing(Dataset::getDatasetVersion, String.CASE_INSENSITIVE_ORDER))
            .map(dataset ->
                new DatasetResponse(
                    dataset.getId(),
                    dataset.getType(),
                    dataset.getSizeValue(),
                    dataset.getSeed(),
                    dataset.getChecksum(),
                    dataset.getDatasetVersion()
                )
            )
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunSummaryResponse> listRecentRuns() {
        List<BenchmarkRun> runs = benchmarkRunRepository.findTop100ByOrderByQueuedAtDesc();
        if (runs.isEmpty()) {
            return List.of();
        }
        Map<Long, RunMetric> metricsByRunId = runMetricRepository
            .findByBenchmarkRunIdIn(runs.stream().map(BenchmarkRun::getId).toList())
            .stream()
            .collect(Collectors.toMap(metric -> metric.getBenchmarkRun().getId(), metric -> metric));
        return runs
            .stream()
            .map(run -> {
                RunMetric metric = metricsByRunId.get(run.getId());
                return new RunSummaryResponse(
                    run.getId(),
                    run.getStatus().name(),
                    run.getImplementation().getLanguage().name(),
                    run.getImplementation().getAlgorithm().getId(),
                    run.getImplementation().getAlgorithm().getName(),
                    run.getDataset().getId(),
                    run.getDataset().getSizeValue(),
                    run.getQueuedAt() == null ? null : run.getQueuedAt().toString(),
                    run.getFinishedAt() == null ? null : run.getFinishedAt().toString(),
                    metric == null ? null : metric.getCpuTimeMs(),
                    metric == null ? null : metric.getOrchestrationWallTimeMs(),
                    metric == null ? null : metric.getExecutionWallTimeMs()
                );
            })
            .toList();
    }

    @Override
    public void markRunStarted(Long runId, String runnerHost) {
        BenchmarkRun run = benchmarkRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        if (!EnumSet.of(BenchmarkRunStatus.QUEUED, BenchmarkRunStatus.RUNNING).contains(run.getStatus())) {
            return;
        }
        run.setStatus(BenchmarkRunStatus.RUNNING);
        run.setRunnerHost(runnerHost);
        if (run.getStartedAt() == null) {
            run.setStartedAt(Instant.now());
        }
        benchmarkRunRepository.save(run);
    }

    @Override
    public void registerRunResult(Long runId, RunResultCallbackRequest request) {
        BenchmarkRun run = benchmarkRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        if (
            !EnumSet.of(
                BenchmarkRunStatus.SUCCEEDED,
                BenchmarkRunStatus.FAILED,
                BenchmarkRunStatus.TIMEOUT,
                BenchmarkRunStatus.COMPILE_ERROR,
                BenchmarkRunStatus.RUNTIME_ERROR
            ).contains(request.status())
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Worker callback status must be terminal");
        }
        run.setStatus(request.status());
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
        metric.setOrchestrationWallTimeMs(request.orchestrationWallTimeMs());
        metric.setExecutionWallTimeMs(request.executionWallTimeMs());
        metric.setPeakMemoryMb(request.peakMemoryMb());
        metric.setExitCode(request.exitCode());
        metric.setTimedOut(request.timedOut());
        metric.setCompileWallTimeMs(request.compileWallTimeMs());
        runMetricRepository.save(metric);

        RunArtifact artifact = runArtifactRepository.findByBenchmarkRunId(runId).orElseGet(RunArtifact::new);
        artifact.setBenchmarkRun(run);
        artifact.setStdoutPreview(request.stdoutPreview());
        artifact.setStderrPreview(request.stderrPreview());
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
        var grouped = benchmarkRunRepository
            .findComparisonSamples(algorithmId, datasetId, BenchmarkRunStatus.SUCCEEDED)
            .stream()
            .collect(Collectors.groupingBy(sample -> sample.getLanguage().name()));

        List<BenchmarkCompareRow> rows = new ArrayList<>();
        grouped.forEach((language, languageRuns) -> {
            List<Long> values = languageRuns
                .stream()
                .map(BenchmarkMetricSample::getExecutionWallTimeMs)
                .filter(value -> value != null)
                .sorted()
                .toList();
            if (values.isEmpty()) {
                return;
            }
            double avg = values.stream().mapToLong(Long::longValue).average().orElse(0);
            double stddev = calcStdDev(values, avg);
            double p50 = percentile(values, 50);
            double p95 = percentile(values, 95);
            rows.add(new BenchmarkCompareRow(language, avg, stddev, p50, p95, values.size()));
        });

        rows.sort(Comparator.comparing(BenchmarkCompareRow::avg));
        return new BenchmarkCompareResponse(algorithmId, datasetId, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkTimeseriesResponse timeseries(Long algorithmId, String language) {
        ImplementationLanguage selectedLanguage;
        try {
            selectedLanguage = ImplementationLanguage.valueOf(language.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language", ex);
        }
        List<BenchmarkTimeseriesPoint> points = benchmarkRunRepository
            .findTimeseriesSamples(algorithmId, selectedLanguage, BenchmarkRunStatus.SUCCEEDED)
            .stream()
            .filter(sample -> sample.getExecutionWallTimeMs() != null)
            .map(sample -> new BenchmarkTimeseriesPoint(sample.getFinishedAt().toString(), sample.getExecutionWallTimeMs()))
            .sorted(Comparator.comparing(BenchmarkTimeseriesPoint::finishedAt))
            .toList();

        return new BenchmarkTimeseriesResponse(algorithmId, language, points);
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkComplexityResponse complexity(Long algorithmId, String metric) {
        String selectedMetric = metric == null || metric.isBlank() ? "cpuTimeMs" : metric;
        if (
            !selectedMetric.equals("cpuTimeMs") &&
            !selectedMetric.equals("executionWallTimeMs") &&
            !selectedMetric.equals("orchestrationWallTimeMs")
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported metric");
        }

        Map<String, Map<Long, List<BenchmarkMetricSample>>> grouped = benchmarkRunRepository
            .findComplexitySamples(algorithmId, BenchmarkRunStatus.SUCCEEDED)
            .stream()
            .collect(
                Collectors.groupingBy(
                    sample -> sample.getLanguage().name(),
                    LinkedHashMap::new,
                    Collectors.groupingBy(BenchmarkMetricSample::getDatasetId, LinkedHashMap::new, Collectors.toList())
                )
            );

        List<BenchmarkComplexitySeries> series = grouped
            .entrySet()
            .stream()
            .map(languageEntry -> {
                List<BenchmarkComplexityPoint> points = languageEntry
                    .getValue()
                    .values()
                    .stream()
                    .map(samples -> toComplexityPoint(samples, selectedMetric))
                    .filter(point -> point != null)
                    .sorted(Comparator.comparing(BenchmarkComplexityPoint::datasetSize))
                    .toList();
                return new BenchmarkComplexitySeries(languageEntry.getKey(), points);
            })
            .filter(item -> !item.points().isEmpty())
            .sorted(Comparator.comparing(BenchmarkComplexitySeries::language))
            .toList();

        return new BenchmarkComplexityResponse(algorithmId, selectedMetric, series);
    }

    private static RunResponse toRunResponse(BenchmarkRun run, RunMetric metric, RunArtifact artifact) {
        RunMetricResponse metricResponse = metric == null
            ? null
            : new RunMetricResponse(
                metric.getCpuTimeMs(),
                metric.getOrchestrationWallTimeMs(),
                metric.getExecutionWallTimeMs(),
                metric.getPeakMemoryMb(),
                metric.getExitCode(),
                metric.getTimedOut(),
                metric.getCompileWallTimeMs()
            );

        RunArtifactResponse artifactResponse = artifact == null
            ? null
            : new RunArtifactResponse(
                artifact.getStdoutPreview(),
                artifact.getStderrPreview(),
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

    private static double percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        double rank = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);
        double lower = sortedValues.get(lowerIndex);
        double upper = sortedValues.get(upperIndex);
        return lower + ((rank - lowerIndex) * (upper - lower));
    }

    private BenchmarkComplexityPoint toComplexityPoint(List<BenchmarkMetricSample> datasetSamples, String metric) {
        if (datasetSamples.isEmpty()) {
            return null;
        }
        List<Long> values = datasetSamples
            .stream()
            .map(sample -> metricValue(sample, metric))
            .filter(value -> value != null)
            .sorted()
            .toList();
        if (values.isEmpty()) {
            return null;
        }
        BenchmarkMetricSample firstSample = datasetSamples.get(0);
        double avg = values.stream().mapToLong(Long::longValue).average().orElse(0);
        return new BenchmarkComplexityPoint(
            firstSample.getDatasetId(),
            firstSample.getDatasetSize(),
            avg,
            calcStdDev(values, avg),
            percentile(values, 50),
            percentile(values, 95),
            values.size()
        );
    }

    private static Long metricValue(BenchmarkMetricSample sample, String metric) {
        return switch (metric) {
            case "cpuTimeMs" -> sample.getCpuTimeMs();
            case "executionWallTimeMs" -> sample.getExecutionWallTimeMs();
            default -> sample.getOrchestrationWallTimeMs();
        };
    }

    private void validateSourceCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source code is required");
        }
        int sourceBytes = sourceCode.getBytes(StandardCharsets.UTF_8).length;
        if (sourceBytes > benchLabProperties.getLimits().getMaxSourceBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source code exceeds the configured byte limit");
        }
    }

    private void validateDatasetSize(Long datasetSize) {
        if (datasetSize == null || datasetSize < 1 || datasetSize > benchLabProperties.getLimits().getMaxDatasetSize()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dataset size is outside the configured range");
        }
    }

    private void validateRunLimits(int timeoutMs, int memoryMb, double cpuLimit, int iterations, int warmupIterations) {
        BenchLabProperties.Limits limits = benchLabProperties.getLimits();
        if (timeoutMs < 100 || timeoutMs > limits.getMaxTimeoutMs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timeout is outside the configured range");
        }
        if (memoryMb < 32 || memoryMb > limits.getMaxMemoryMb()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory is outside the configured range");
        }
        if (!Double.isFinite(cpuLimit) || cpuLimit < 0.1 || cpuLimit > limits.getMaxCpuLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPU limit is outside the configured range");
        }
        if (iterations < 1 || iterations > limits.getMaxIterations()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Iterations are outside the configured range");
        }
        if (warmupIterations < 0 || warmupIterations > limits.getMaxWarmupIterations()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warmup iterations are outside the configured range");
        }
    }
}
