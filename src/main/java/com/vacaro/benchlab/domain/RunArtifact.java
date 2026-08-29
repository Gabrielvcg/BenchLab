package com.vacaro.benchlab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "run_artifact")
public class RunArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_run_id", nullable = false, unique = true)
    private BenchmarkRun benchmarkRun;

    @Column(name = "stdout_truncated")
    private String stdoutPreview;

    @Column(name = "stderr_truncated")
    private String stderrPreview;

    @Column(name = "stdout_was_truncated", nullable = false)
    private Boolean stdoutTruncated = false;

    @Column(name = "stderr_was_truncated", nullable = false)
    private Boolean stderrTruncated = false;

    @Column(name = "output_size_bytes")
    private Long outputSizeBytes;

    @Column(name = "artifact_checksum")
    private String artifactChecksum;

    @Column(name = "technical_log_summary")
    private String technicalLogSummary;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BenchmarkRun getBenchmarkRun() {
        return benchmarkRun;
    }

    public void setBenchmarkRun(BenchmarkRun benchmarkRun) {
        this.benchmarkRun = benchmarkRun;
    }

    public String getStdoutPreview() {
        return stdoutPreview;
    }

    public void setStdoutPreview(String stdoutPreview) {
        this.stdoutPreview = stdoutPreview;
    }

    public String getStderrPreview() {
        return stderrPreview;
    }

    public void setStderrPreview(String stderrPreview) {
        this.stderrPreview = stderrPreview;
    }

    public Boolean getStdoutTruncated() {
        return stdoutTruncated;
    }

    public void setStdoutTruncated(Boolean stdoutTruncated) {
        this.stdoutTruncated = stdoutTruncated;
    }

    public Boolean getStderrTruncated() {
        return stderrTruncated;
    }

    public void setStderrTruncated(Boolean stderrTruncated) {
        this.stderrTruncated = stderrTruncated;
    }

    public Long getOutputSizeBytes() {
        return outputSizeBytes;
    }

    public void setOutputSizeBytes(Long outputSizeBytes) {
        this.outputSizeBytes = outputSizeBytes;
    }

    public String getArtifactChecksum() {
        return artifactChecksum;
    }

    public void setArtifactChecksum(String artifactChecksum) {
        this.artifactChecksum = artifactChecksum;
    }

    public String getTechnicalLogSummary() {
        return technicalLogSummary;
    }

    public void setTechnicalLogSummary(String technicalLogSummary) {
        this.technicalLogSummary = technicalLogSummary;
    }
}
