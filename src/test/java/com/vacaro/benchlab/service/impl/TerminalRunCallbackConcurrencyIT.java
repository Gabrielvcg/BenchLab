package com.vacaro.benchlab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import com.vacaro.benchlab.IntegrationTest;
import com.vacaro.benchlab.domain.*;
import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.dto.benchmark.RunResultCallbackRequest;
import com.vacaro.benchlab.service.messaging.RunEventPublisher;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@EnabledIfEnvironmentVariable(named = "BENCHLAB_TEST_CALLBACK_CONCURRENCY", matches = "true")
class TerminalRunCallbackConcurrencyIT {
    @Autowired private BenchmarkService service;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private RunEventPublisher publisher;

    private RunResultCallbackRequest result(BenchmarkRunStatus status, long metric) {
        return new RunResultCallbackRequest(status, "fixture-host", null, metric, metric, metric, null,
            0, false, 0L, "fixture", null, false, false, 7L, "fixture", null);
    }

    private Long[] seedRun(TransactionTemplate tx) {
        return tx.execute(status -> {
            var algorithm = new Algorithm();
            algorithm.setName("callback-lock-" + UUID.randomUUID()); algorithm.setCategory("fixture");
            algorithm.setVersion("1"); algorithm.setComplexityDeclared("O(1)"); em.persist(algorithm);
            var dataset = new Dataset(); dataset.setType("fixture"); dataset.setSizeValue(1L);
            dataset.setSeed(1L); dataset.setChecksum("fixture"); dataset.setDatasetVersion("1"); em.persist(dataset);
            var implementation = new Implementation(); implementation.setAlgorithm(algorithm);
            implementation.setLanguage(ImplementationLanguage.JAVA); implementation.setSourceCode("fixture");
            implementation.setImplementationHash("fixture"); em.persist(implementation);
            var run = new BenchmarkRun(); run.setImplementation(implementation); run.setDataset(dataset);
            run.setStatus(BenchmarkRunStatus.RUNNING); run.setQueuedAt(Instant.now());
            run.setRequestedBy("fixture"); run.setTraceId(UUID.randomUUID().toString()); em.persist(run); em.flush();
            return new Long[] { run.getId(), implementation.getId(), dataset.getId(), algorithm.getId() };
        });
    }

    @org.junit.jupiter.api.Test
    void artifactWriteFailureRollsBackResultAndMetricThenRetryCompletes() {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = seedRun(tx);
        String function = "fixture_reject_artifact_" + ids[0];
        String trigger = "fixture_reject_artifact_" + ids[0];
        try {
            jdbc.execute("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.benchmark_run_id = " + ids[0] + " THEN RAISE EXCEPTION 'fixture write failure'; END IF; RETURN NEW; END; $$");
            jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON run_artifact FOR EACH ROW EXECUTE FUNCTION " + function + "()");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.registerRunResult(ids[0], result(BenchmarkRunStatus.SUCCEEDED, 10)))
                .isInstanceOf(RuntimeException.class);
            tx.executeWithoutResult(status -> {
                assertThat(em.find(BenchmarkRun.class, ids[0]).getStatus()).isEqualTo(BenchmarkRunStatus.RUNNING);
                assertThat(em.find(BenchmarkRun.class, ids[0]).getFinishedAt()).isNull();
                assertThat(em.createQuery("select count(m) from RunMetric m where m.benchmarkRun.id = :id", Long.class)
                    .setParameter("id", ids[0]).getSingleResult()).isZero();
            });
            jdbc.execute("DROP TRIGGER " + trigger + " ON run_artifact");
            service.registerRunResult(ids[0], result(BenchmarkRunStatus.SUCCEEDED, 10));
            tx.executeWithoutResult(status -> assertThat(em.find(BenchmarkRun.class, ids[0]).getStatus()).isEqualTo(BenchmarkRunStatus.SUCCEEDED));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON run_artifact");
            jdbc.execute("DROP FUNCTION IF EXISTS " + function + "()");
            tx.executeWithoutResult(status -> {
                for (String entity : new String[] { "RunArtifact", "RunMetric" })
                    em.createQuery("delete from " + entity + " e where e.benchmarkRun.id = :id").setParameter("id", ids[0]).executeUpdate();
                for (int i = 0; i < ids.length; i++) {
                    String entity = new String[] { "BenchmarkRun", "Implementation", "Dataset", "Algorithm" }[i];
                    em.createQuery("delete from " + entity + " e where e.id = :id").setParameter("id", ids[i]).executeUpdate();
                }
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void committedResultCannotBeReplacedByConcurrentResultOrLateStart(boolean lateStart) throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = seedRun(tx);
        var pool = Executors.newFixedThreadPool(2);
        var firstWritten = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondPid = new CompletableFuture<Integer>();
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                service.registerRunResult(ids[0], result(BenchmarkRunStatus.SUCCEEDED, 10));
                em.flush(); firstWritten.countDown();
                try { if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = pool.submit(() -> tx.executeWithoutResult(status -> {
                secondPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                if (lateStart) service.markRunStarted(ids[0], "late-host");
                else service.registerRunResult(ids[0], result(BenchmarkRunStatus.FAILED, 99));
            }));
            int pid = secondPid.get(5, TimeUnit.SECONDS);
            boolean observedLockWait = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !second.isDone()) {
                observedLockWait = Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')", Boolean.class, pid));
                if (observedLockWait) break;
                Thread.sleep(20);
            }
            assertThat(observedLockWait).as("second real PostgreSQL transaction waits on first row mutation").isTrue();
            releaseFirst.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                var run = em.find(BenchmarkRun.class, ids[0]);
                assertThat(run.getStatus()).isEqualTo(BenchmarkRunStatus.SUCCEEDED);
                assertThat(run.getRunnerHost()).isEqualTo("fixture-host");
                assertThat(em.createQuery("select m.executionWallTimeMs from RunMetric m where m.benchmarkRun.id = :id", Long.class)
                    .setParameter("id", ids[0]).getSingleResult()).isEqualTo(10L);
                assertThat(em.createQuery("select count(a) from RunArtifact a where a.benchmarkRun.id = :id", Long.class)
                    .setParameter("id", ids[0]).getSingleResult()).isEqualTo(1L);
            });
        } finally {
            releaseFirst.countDown(); pool.shutdownNow();
            if (!pool.awaitTermination(15, TimeUnit.SECONDS)) throw new AssertionError("fixture threads did not stop");
            tx.executeWithoutResult(status -> {
                for (String entity : new String[] { "RunArtifact", "RunMetric" })
                    em.createQuery("delete from " + entity + " e where e.benchmarkRun.id = :id").setParameter("id", ids[0]).executeUpdate();
                for (int i = 0; i < ids.length; i++) {
                    String entity = new String[] { "BenchmarkRun", "Implementation", "Dataset", "Algorithm" }[i];
                    em.createQuery("delete from " + entity + " e where e.id = :id").setParameter("id", ids[i]).executeUpdate();
                }
            });
        }
    }
}
