package com.vacaro.benchlab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vacaro.benchlab.config.BenchLabProperties;
import com.vacaro.benchlab.domain.*;
import com.vacaro.benchlab.repository.*;
import com.vacaro.benchlab.service.dto.benchmark.RunResultCallbackRequest;
import com.vacaro.benchlab.service.messaging.RunEventPublisher;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class TerminalRunCallbackTest {
    private final BenchmarkRunRepository runs = mock(BenchmarkRunRepository.class);
    private final RunMetricRepository metrics = mock(RunMetricRepository.class);
    private final RunArtifactRepository artifacts = mock(RunArtifactRepository.class);
    private BenchmarkServiceImpl service;
    private BenchmarkRun run;
    private static final Instant FIRST = Instant.parse("2026-10-24T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-24T11:00:00Z");

    @BeforeEach
    void setup() {
        service = new BenchmarkServiceImpl(mock(AlgorithmRepository.class), mock(DatasetRepository.class),
            mock(ImplementationRepository.class), runs, metrics, artifacts, mock(RunEventPublisher.class),
            new BenchLabProperties(), mock(UserRepository.class));
        run = new BenchmarkRun();
        run.setId(1L);
        run.setStatus(BenchmarkRunStatus.RUNNING);
        run.setStartedAt(FIRST.minusSeconds(30));
    }

    private void storedRun() {
        when(runs.findByIdForUpdate(1L)).thenReturn(Optional.of(run));
    }

    private static RunResultCallbackRequest result(BenchmarkRunStatus status, long value, String output) {
        return new RunResultCallbackRequest(status, "fixture-host", status == BenchmarkRunStatus.SUCCEEDED ? null : "fixture failure",
            value, value + 1, value + 2, 3.0, status == BenchmarkRunStatus.SUCCEEDED ? 0 : 1,
            status == BenchmarkRunStatus.TIMEOUT, value + 3, output, "fixture stderr", false, false,
            14L, "fixture checksum", "fixture summary");
    }

    private record RunSnapshot(BenchmarkRunStatus status, Instant started, Instant finished, String host, String failure) {
        static RunSnapshot of(BenchmarkRun run) {
            return new RunSnapshot(run.getStatus(), run.getStartedAt(), run.getFinishedAt(), run.getRunnerHost(), run.getFailureReason());
        }
    }

    @ParameterizedTest
    @EnumSource(value = BenchmarkRunStatus.class, names = { "SUCCEEDED", "FAILED", "TIMEOUT", "COMPILE_ERROR", "RUNTIME_ERROR" })
    void identicalRetryKeepsFirstTerminalResultAndDoesNotReadOrWriteMetricsOrArtifacts(BenchmarkRunStatus status) {
        storedRun();
        var payload = result(status, 10, "first");
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(FIRST);
            service.registerRunResult(1L, payload);
            var original = RunSnapshot.of(run);
            assertThat(original.finished()).isEqualTo(FIRST);
            clearInvocations(runs, metrics, artifacts);
            clock.when(Instant::now).thenReturn(LATER);
            service.registerRunResult(1L, payload);
            assertThat(RunSnapshot.of(run)).isEqualTo(original);
            verify(runs, never()).save(any());
            verifyNoInteractions(metrics, artifacts);
        }
    }

    @ParameterizedTest
    @EnumSource(value = BenchmarkRunStatus.class, names = { "SUCCEEDED", "FAILED", "TIMEOUT", "COMPILE_ERROR", "RUNTIME_ERROR" })
    void contradictoryTerminalRetryCannotReplaceFirstResult(BenchmarkRunStatus firstStatus) {
        storedRun();
        service.registerRunResult(1L, result(firstStatus, 10, "original"));
        var original = RunSnapshot.of(run);
        clearInvocations(runs, metrics, artifacts);
        service.registerRunResult(1L, result(firstStatus == BenchmarkRunStatus.SUCCEEDED ? BenchmarkRunStatus.FAILED : BenchmarkRunStatus.SUCCEEDED, 900, "changed"));
        assertThat(RunSnapshot.of(run)).isEqualTo(original);
        verify(runs, never()).save(any());
        verifyNoInteractions(metrics, artifacts);
    }

    @Test
    void changedPayloadWithSameTerminalStatusCannotRewriteMetricsAndArtifacts() {
        storedRun();
        service.registerRunResult(1L, result(BenchmarkRunStatus.SUCCEEDED, 10, "original"));
        var original = RunSnapshot.of(run);
        clearInvocations(runs, metrics, artifacts);
        service.registerRunResult(1L, result(BenchmarkRunStatus.SUCCEEDED, 900, "changed"));
        assertThat(RunSnapshot.of(run)).isEqualTo(original);
        verify(runs, never()).save(any());
        verifyNoInteractions(metrics, artifacts);
    }

    @ParameterizedTest
    @EnumSource(value = BenchmarkRunStatus.class, names = { "QUEUED", "RUNNING" })
    void firstCallbackPersistsAllResultPartsAndSetsMissingStartTime(BenchmarkRunStatus initial) {
        storedRun();
        run.setStatus(initial);
        run.setStartedAt(null);
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(FIRST);
            var payload = result(BenchmarkRunStatus.FAILED, 10, "original");
            service.registerRunResult(1L, payload);
            assertThat(run.getStatus()).isEqualTo(BenchmarkRunStatus.FAILED);
            assertThat(run.getStartedAt()).isEqualTo(FIRST);
            assertThat(run.getFinishedAt()).isEqualTo(FIRST);
            assertThat(run.getFailureReason()).isEqualTo(payload.failureReason());
            assertThat(run.getRunnerHost()).isEqualTo(payload.runnerHost());
            verify(runs).save(run);
            var metric = ArgumentCaptor.forClass(RunMetric.class);
            verify(metrics).save(metric.capture());
            assertThat(metric.getValue().getBenchmarkRun()).isSameAs(run);
            assertThat(metric.getValue().getCpuTimeMs()).isEqualTo(payload.cpuTimeMs());
            assertThat(metric.getValue().getExecutionWallTimeMs()).isEqualTo(payload.executionWallTimeMs());
            assertThat(metric.getValue().getCompileWallTimeMs()).isEqualTo(payload.compileWallTimeMs());
            var artifact = ArgumentCaptor.forClass(RunArtifact.class);
            verify(artifacts).save(artifact.capture());
            assertThat(artifact.getValue().getStdoutPreview()).isEqualTo("original");
            assertThat(artifact.getValue().getArtifactChecksum()).isEqualTo(payload.artifactChecksum());
        }
    }

    @ParameterizedTest
    @EnumSource(value = BenchmarkRunStatus.class, names = { "SUCCEEDED", "FAILED", "TIMEOUT", "COMPILE_ERROR", "RUNTIME_ERROR" })
    void lateStartCannotReopenTerminalRun(BenchmarkRunStatus status) {
        storedRun();
        run.setStatus(status);
        run.setFinishedAt(FIRST);
        var original = RunSnapshot.of(run);
        service.markRunStarted(1L, "late-host");
        assertThat(RunSnapshot.of(run)).isEqualTo(original);
        verify(runs, never()).save(any());
        verifyNoInteractions(metrics, artifacts);
    }

    @Test
    void nonterminalResultStillRejectedEvenWhenRunIsAlreadyTerminal() {
        storedRun();
        run.setStatus(BenchmarkRunStatus.SUCCEEDED);
        assertThatThrownBy(() -> service.registerRunResult(1L, result(BenchmarkRunStatus.RUNNING, 10, "invalid")))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
        verify(runs, never()).save(any());
        verifyNoInteractions(metrics, artifacts);
    }

    @Test
    void missingRunRemainsNotFound() {
        assertThatThrownBy(() -> service.registerRunResult(999L, result(BenchmarkRunStatus.SUCCEEDED, 10, "missing")))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(metrics, artifacts);
    }

    @Test
    void transactionalFailureRollsBackFixtureAndRetryCanComplete() {
        storedRun();
        when(metrics.save(any())).thenThrow(new IllegalStateException("fixture persistence failure")).thenAnswer(call -> call.getArgument(0));
        java.util.List<String> outcomes = new java.util.ArrayList<>();
        var manager = new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            private RunSnapshot before;
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition definition) {
                before = RunSnapshot.of(run);
            }
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                outcomes.add("commit");
            }
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
                // In-memory resource rollback, not a substitute for PostgreSQL integration.
                run.setStatus(before.status()); run.setStartedAt(before.started()); run.setFinishedAt(before.finished());
                run.setRunnerHost(before.host()); run.setFailureReason(before.failure());
                outcomes.add("rollback");
            }
        };
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.setInterfaces(com.vacaro.benchlab.service.BenchmarkService.class);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
            manager, new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()
        ));
        var proxy = (com.vacaro.benchlab.service.BenchmarkService) factory.getProxy();
        var original = RunSnapshot.of(run);
        assertThatThrownBy(() -> proxy.registerRunResult(1L, result(BenchmarkRunStatus.FAILED, 10, "retry")))
            .isInstanceOf(IllegalStateException.class);
        assertThat(RunSnapshot.of(run)).isEqualTo(original);
        verifyNoInteractions(artifacts);
        proxy.registerRunResult(1L, result(BenchmarkRunStatus.FAILED, 10, "retry"));
        assertThat(run.getStatus()).isEqualTo(BenchmarkRunStatus.FAILED);
        assertThat(outcomes).containsExactly("rollback", "commit");
        verify(artifacts).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = BenchmarkRunStatus.class, names = { "QUEUED", "RUNNING" })
    void startStillAllowsActiveRunsAndPreservesExistingStartTime(BenchmarkRunStatus initial) {
        storedRun();
        run.setStatus(initial);
        service.markRunStarted(1L, "first-host");
        assertThat(run.getStatus()).isEqualTo(BenchmarkRunStatus.RUNNING);
        assertThat(run.getStartedAt()).isEqualTo(FIRST.minusSeconds(30));
        assertThat(run.getRunnerHost()).isEqualTo("first-host");
        verify(runs).save(run);
    }

    @Test
    void terminalMutationLookupDeclaresPessimisticWriteLock() throws Exception {
        var method = BenchmarkRunRepository.class.getMethod("findByIdForUpdate", Long.class);
        assertThat(method.getAnnotation(org.springframework.data.jpa.repository.Lock.class).value())
            .isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertThat(BenchmarkServiceImpl.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNotNull();
    }

    @Test
    void persistenceFailurePropagatesWithoutContinuingToArtifacts() {
        storedRun();
        when(metrics.save(any())).thenThrow(new IllegalStateException("fixture persistence failure"));
        assertThatThrownBy(() -> service.registerRunResult(1L, result(BenchmarkRunStatus.SUCCEEDED, 10, "first")))
            .isInstanceOf(IllegalStateException.class).hasMessage("fixture persistence failure");
        verifyNoInteractions(artifacts);
    }
}
