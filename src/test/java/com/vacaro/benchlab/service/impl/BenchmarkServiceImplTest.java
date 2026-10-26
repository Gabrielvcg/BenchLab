package com.vacaro.benchlab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vacaro.benchlab.config.BenchLabProperties;
import com.vacaro.benchlab.domain.Algorithm;
import com.vacaro.benchlab.domain.BenchmarkRun;
import com.vacaro.benchlab.domain.BenchmarkRunStatus;
import com.vacaro.benchlab.domain.Dataset;
import com.vacaro.benchlab.domain.Implementation;
import com.vacaro.benchlab.domain.ImplementationLanguage;
import com.vacaro.benchlab.domain.RunMetric;
import com.vacaro.benchlab.domain.User;
import com.vacaro.benchlab.repository.AlgorithmRepository;
import com.vacaro.benchlab.repository.BenchmarkMetricSample;
import com.vacaro.benchlab.repository.BenchmarkRunRepository;
import com.vacaro.benchlab.repository.DatasetRepository;
import com.vacaro.benchlab.repository.ImplementationRepository;
import com.vacaro.benchlab.repository.RunArtifactRepository;
import com.vacaro.benchlab.repository.RunMetricRepository;
import com.vacaro.benchlab.repository.UserRepository;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkTimeseriesPoint;
import com.vacaro.benchlab.service.dto.benchmark.CreateDatasetRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateImplementationRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateRunRequest;
import com.vacaro.benchlab.service.messaging.RunEventPublisher;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceImplTest {

    @Mock
    private AlgorithmRepository algorithmRepository;

    @Mock
    private DatasetRepository datasetRepository;

    @Mock
    private ImplementationRepository implementationRepository;

    @Mock
    private BenchmarkRunRepository benchmarkRunRepository;

    @Mock
    private RunMetricRepository runMetricRepository;

    @Mock
    private RunArtifactRepository runArtifactRepository;

    @Mock
    private RunEventPublisher runEventPublisher;

    @Mock
    private UserRepository userRepository;

    private BenchLabProperties properties;
    private BenchmarkServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new BenchLabProperties();
        service = new BenchmarkServiceImpl(
            algorithmRepository,
            datasetRepository,
            implementationRepository,
            benchmarkRunRepository,
            runMetricRepository,
            runArtifactRepository,
            runEventPublisher,
            properties,
            userRepository
        );
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("demo-user", "n/a"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static Stream<Arguments> numericMeanCases() {
        List<List<Long>> values = List.of(
            List.of(1L, 2L, 6L),
            List.of(7L),
            List.of(0L, 0L),
            Arrays.asList(null, 3L, null, 9L),
            List.of(1L << 62, 1L << 62),
            List.of(0L, 1L, Long.MAX_VALUE),
            List.of(Long.MAX_VALUE),
            List.of(Long.MAX_VALUE, Long.MAX_VALUE - 1),
            List.of(0L, Long.MAX_VALUE),
            List.of(1L << 62, 1L << 62, 1L << 62)
        );
        return values.stream().flatMap(items -> Stream.of("compare", "cpuTimeMs", "executionWallTimeMs", "orchestrationWallTimeMs")
            .map(path -> Arguments.of(path, items)));
    }

    @ParameterizedTest
    @MethodSource("numericMeanCases")
    void numericMeansStayWithinNonnegativeBoundsUsingIndependentDecimalReference(String path, List<Long> values) {
        List<BenchmarkMetricSample> samples = values.stream().map(value -> numericSample(path, value)).toList();
        double avg;
        double stddev;
        int count;
        if (path.equals("compare")) {
            when(benchmarkRunRepository.findComparisonSamples(7L, 2L, BenchmarkRunStatus.SUCCEEDED)).thenReturn(samples);
            var rows = service.compare(7L, 2L).rows();
            assertThat(rows).hasSize(1);
            avg = rows.get(0).avg();
            stddev = rows.get(0).stddev();
            count = rows.get(0).validSamples();
        } else {
            when(benchmarkRunRepository.findComplexitySamples(7L, BenchmarkRunStatus.SUCCEEDED)).thenReturn(samples);
            var series = service.complexity(7L, path).series();
            assertThat(series).hasSize(1);
            assertThat(series.get(0).points()).hasSize(1);
            var point = series.get(0).points().get(0);
            avg = point.avg();
            stddev = point.stddev();
            count = point.validSamples();
        }
        List<Long> present = values.stream().filter(value -> value != null).toList();
        BigDecimal total = present.stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        double expected = total.divide(BigDecimal.valueOf(present.size()), MathContext.DECIMAL128).doubleValue();
        // Conversion and floating-point accumulation may round; no exact-decimal API contract is implied.
        double tolerance = Math.max(4 * Math.ulp(expected), 1e-12);
        assertThat(avg).isFinite().isGreaterThanOrEqualTo(0.0);
        assertThat(avg).isBetween(present.stream().min(Long::compare).orElseThrow().doubleValue(),
            present.stream().max(Long::compare).orElseThrow().doubleValue());
        assertThat(avg).isCloseTo(expected, org.assertj.core.data.Offset.offset(tolerance));
        assertThat(count).isEqualTo(present.size());
        assertThat(stddev).isFinite().isGreaterThanOrEqualTo(0.0);
        if (present.stream().distinct().count() == 1) {
            assertThat(stddev).isZero();
        }
    }

    private static BenchmarkMetricSample numericSample(String selectedMetric, Long value) {
        BenchmarkMetricSample sample = mock(BenchmarkMetricSample.class);
        // Lenient only for projection fields unused by some public aggregation paths.
        org.mockito.Mockito.lenient().when(sample.getLanguage()).thenReturn(ImplementationLanguage.JAVA);
        org.mockito.Mockito.lenient().when(sample.getDatasetId()).thenReturn(2L);
        org.mockito.Mockito.lenient().when(sample.getDatasetSize()).thenReturn(100L);
        org.mockito.Mockito.lenient().when(sample.getCpuTimeMs()).thenReturn(selectedMetric.equals("cpuTimeMs") ? value : Long.valueOf(777L));
        org.mockito.Mockito.lenient().when(sample.getExecutionWallTimeMs())
            .thenReturn(selectedMetric.equals("executionWallTimeMs") || selectedMetric.equals("compare") ? value : Long.valueOf(888L));
        org.mockito.Mockito.lenient().when(sample.getOrchestrationWallTimeMs())
            .thenReturn(selectedMetric.equals("orchestrationWallTimeMs") ? value : Long.valueOf(999L));
        return sample;
    }

    @Test
    void timeseriesPreservesChronologicalQueryOrderAcrossTimestampPrecisionsAndTies() {
        List<String> timestamps = List.of(
            "2026-10-24T11:59:59.999999999Z",
            "2026-10-24T12:00:00Z",
            "2026-10-24T12:00:00.000000001Z",
            "2026-10-24T12:00:00.000001Z",
            "2026-10-24T12:00:00.100Z",
            "2026-10-24T12:00:00.100Z",
            "2026-10-24T12:00:01Z"
        );
        List<BenchmarkMetricSample> samples = java.util.stream.IntStream.range(0, timestamps.size())
            .mapToObj(index -> timeseriesSample(Instant.parse(timestamps.get(index)), 10L + index))
            .toList();
        when(benchmarkRunRepository.findTimeseriesSamples(7L, ImplementationLanguage.JAVA, BenchmarkRunStatus.SUCCEEDED))
            .thenReturn(samples);

        var response = service.timeseries(7L, "java");

        assertThat(response.points()).containsExactlyElementsOf(
            java.util.stream.IntStream.range(0, timestamps.size())
                .mapToObj(index -> new BenchmarkTimeseriesPoint(timestamps.get(index), 10L + index))
                .toList()
        );
        verify(benchmarkRunRepository).findTimeseriesSamples(7L, ImplementationLanguage.JAVA, BenchmarkRunStatus.SUCCEEDED);
    }

    @Test
    void timeseriesReturnsEmptyForEmptyQuery() {
        when(benchmarkRunRepository.findTimeseriesSamples(7L, ImplementationLanguage.JAVA, BenchmarkRunStatus.SUCCEEDED))
            .thenReturn(List.of());

        assertThat(service.timeseries(7L, "JAVA").points()).isEmpty();
    }

    @Test
    void timeseriesStillFiltersUnavailableMetricsWithoutReorderingRemainingPoints() {
        Instant start = Instant.parse("2026-10-24T12:00:00Z");
        List<BenchmarkMetricSample> samples = List.of(
            timeseriesSample(start, 4L), timeseriesSample(start, null), timeseriesSample(start.plusMillis(100), 9L)
        );
        when(benchmarkRunRepository.findTimeseriesSamples(7L, ImplementationLanguage.JAVA, BenchmarkRunStatus.SUCCEEDED))
            .thenReturn(samples);

        assertThat(service.timeseries(7L, "JAVA").points()).containsExactly(
            new BenchmarkTimeseriesPoint(start.toString(), 4L),
            new BenchmarkTimeseriesPoint(start.plusMillis(100).toString(), 9L)
        );
    }

    private static BenchmarkMetricSample timeseriesSample(Instant finishedAt, Long metric) {
        BenchmarkMetricSample sample = mock(BenchmarkMetricSample.class);
        when(sample.getExecutionWallTimeMs()).thenReturn(metric);
        if (metric != null) {
            when(sample.getFinishedAt()).thenReturn(finishedAt);
        }
        return sample;
    }

    @Test
    void historyIsScopedToAuthenticatedUserAndUsesExclusiveCursor() {
        when(benchmarkRunRepository.findTop100ByRequestedByAndIdLessThanOrderByIdDesc("demo-user", Long.MAX_VALUE)).thenReturn(List.of());
        when(benchmarkRunRepository.findTop100ByRequestedByAndIdLessThanOrderByIdDesc("demo-user", 123L)).thenReturn(List.of());
        assertThat(service.listRunHistory(null)).isEmpty();
        assertThat(service.listRunHistory(123L)).isEmpty();
        verify(benchmarkRunRepository).findTop100ByRequestedByAndIdLessThanOrderByIdDesc("demo-user", Long.MAX_VALUE);
        verify(benchmarkRunRepository).findTop100ByRequestedByAndIdLessThanOrderByIdDesc("demo-user", 123L);
    }

    @Test
    void historyRejectsInvalidCursorAndMissingAuthentication() {
        assertThatThrownBy(() -> service.listRunHistory(0L)).isInstanceOf(ResponseStatusException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.listRunHistory(null)).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
        );
    }

    @Test
    void rejectsSourceThatExceedsConfiguredByteLimit() {
        String source = "x".repeat(properties.getLimits().getMaxSourceBytes() + 1);

        assertThatThrownBy(() ->
            service.createImplementation(new CreateImplementationRequest(1L, ImplementationLanguage.PYTHON, source, "", ""))
        ).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );

        verify(algorithmRepository, never()).findById(1L);
    }

    @Test
    void appliesSourceLimitToUtf8BytesRatherThanCharacters() {
        String source = "á".repeat(properties.getLimits().getMaxSourceBytes() / 2 + 1);

        assertThatThrownBy(() ->
            service.createImplementation(new CreateImplementationRequest(1L, ImplementationLanguage.PYTHON, source, "", ""))
        ).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );

        verify(algorithmRepository, never()).findById(1L);
    }

    @Test
    void rejectsDatasetThatExceedsConfiguredLimit() {
        CreateDatasetRequest request = new CreateDatasetRequest(
            "synthetic",
            properties.getLimits().getMaxDatasetSize() + 1,
            42L,
            "checksum",
            "v1"
        );

        assertThatThrownBy(() -> service.createDataset(request)).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );

        verify(datasetRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRunWhenUserReachedOutstandingLimit() {
        when(userRepository.lockOneByLogin("demo-user")).thenReturn(Optional.of(new User()));
        when(
            benchmarkRunRepository.countByRequestedByAndStatusIn(
                "demo-user",
                List.of(BenchmarkRunStatus.QUEUED, BenchmarkRunStatus.RUNNING)
            )
        ).thenReturn(properties.getLimits().getMaxOutstandingRunsPerUser());

        assertThatThrownBy(() -> service.createRun(new CreateRunRequest(1L, 2L, 5000, 256, 1.0, 2, 0))).isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        );

        verify(implementationRepository, never()).findById(1L);
    }

    @ParameterizedTest(name = "rejects invalid run limit: {0}")
    @MethodSource("invalidRunRequests")
    void rejectsRunValuesOutsideConfiguredLimits(String ignoredDescription, CreateRunRequest request) {
        assertThatThrownBy(() -> service.createRun(request)).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );

        verify(userRepository, never()).lockOneByLogin("demo-user");
        verify(benchmarkRunRepository, never()).countByRequestedByAndStatusIn(
            "demo-user",
            List.of(BenchmarkRunStatus.QUEUED, BenchmarkRunStatus.RUNNING)
        );
    }

    private static Stream<Arguments> invalidRunRequests() {
        return Stream.of(
            Arguments.of("timeout", new CreateRunRequest(1L, 2L, 30_001, 256, 1.0, 2, 0)),
            Arguments.of("memory", new CreateRunRequest(1L, 2L, 5000, 513, 1.0, 2, 0)),
            Arguments.of("cpu", new CreateRunRequest(1L, 2L, 5000, 256, 2.1, 2, 0)),
            Arguments.of("iterations", new CreateRunRequest(1L, 2L, 5000, 256, 1.0, 11, 0)),
            Arguments.of("warmups", new CreateRunRequest(1L, 2L, 5000, 256, 1.0, 2, 4))
        );
    }

    @Test
    void loadsRecentMetricsInOneBulkRepositoryCall() {
        Algorithm algorithm = new Algorithm();
        algorithm.setId(10L);
        algorithm.setName("linear-sum");
        Implementation implementation = new Implementation();
        implementation.setId(20L);
        implementation.setAlgorithm(algorithm);
        implementation.setLanguage(ImplementationLanguage.GO);
        Dataset dataset = new Dataset();
        dataset.setId(30L);
        dataset.setSizeValue(1000L);
        BenchmarkRun run = new BenchmarkRun();
        run.setId(40L);
        run.setImplementation(implementation);
        run.setDataset(dataset);
        run.setStatus(BenchmarkRunStatus.SUCCEEDED);
        run.setQueuedAt(Instant.parse("2026-08-10T10:00:00Z"));
        RunMetric metric = new RunMetric();
        metric.setBenchmarkRun(run);
        metric.setOrchestrationWallTimeMs(12L);

        when(benchmarkRunRepository.findTop100ByOrderByQueuedAtDesc()).thenReturn(List.of(run));
        when(runMetricRepository.findByBenchmarkRunIdIn(anyCollection())).thenReturn(List.of(metric));

        var result = service.listRecentRuns();

        assertThat(result).singleElement().satisfies(summary -> assertThat(summary.orchestrationWallTimeMs()).isEqualTo(12L));
        verify(runMetricRepository).findByBenchmarkRunIdIn(List.of(40L));
        verify(runMetricRepository, never()).findByBenchmarkRunId(40L);
    }

    @Test
    void interpolatesPercentilesWhenComplexityHasAnEvenNumberOfSamples() {
        BenchmarkMetricSample first = mock(BenchmarkMetricSample.class);
        when(first.getLanguage()).thenReturn(ImplementationLanguage.C);
        when(first.getDatasetId()).thenReturn(30L);
        when(first.getDatasetSize()).thenReturn(1_000_000L);
        when(first.getCpuTimeMs()).thenReturn(12L);

        BenchmarkMetricSample second = mock(BenchmarkMetricSample.class);
        when(second.getLanguage()).thenReturn(ImplementationLanguage.C);
        when(second.getDatasetId()).thenReturn(30L);
        when(second.getCpuTimeMs()).thenReturn(18L);

        when(benchmarkRunRepository.findComplexitySamples(99L, BenchmarkRunStatus.SUCCEEDED)).thenReturn(List.of(first, second));

        var result = service.complexity(99L, "cpuTimeMs");

        assertThat(result.series())
            .singleElement()
            .satisfies(series ->
                assertThat(series.points())
                    .singleElement()
                    .satisfies(point -> {
                        assertThat(point.p50()).isEqualTo(15.0);
                        assertThat(point.p95()).isEqualTo(17.7);
                    })
            );
    }
}
