package com.vacaro.benchlab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "benchmark_run")
public class BenchmarkRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "implementation_id", nullable = false)
    private Implementation implementation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BenchmarkRunStatus status;

    @Column(name = "trace_id", nullable = false, unique = true)
    private String traceId;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "runner_host")
    private String runnerHost;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Implementation getImplementation() { return implementation; }
    public void setImplementation(Implementation implementation) { this.implementation = implementation; }
    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
    public BenchmarkRunStatus getStatus() { return status; }
    public void setStatus(BenchmarkRunStatus status) { this.status = status; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getQueuedAt() { return queuedAt; }
    public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getRunnerHost() { return runnerHost; }
    public void setRunnerHost(String runnerHost) { this.runnerHost = runnerHost; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
}
