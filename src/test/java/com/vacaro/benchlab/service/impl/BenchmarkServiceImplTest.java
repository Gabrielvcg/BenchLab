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
import com.vacaro.benchlab.service.dto.benchmark.CreateDatasetRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateImplementationRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateRunRequest;
import com.vacaro.benchlab.service.messaging.RunEventPublisher;
import java.time.Instant;
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
