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
@Table(name = "run_metric")
public class RunMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_run_id", nullable = false, unique = true)
    private BenchmarkRun benchmarkRun;

    @Column(name = "cpu_time_ms")
    private Long cpuTimeMs;

    @Column(name = "wall_time_ms")
    private Long wallTimeMs;

    @Column(name = "peak_memory_mb")
    private Double peakMemoryMb;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "timed_out")
    private Boolean timedOut;

    @Column(name = "compile_ms")
    private Long compileMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BenchmarkRun getBenchmarkRun() { return benchmarkRun; }
    public void setBenchmarkRun(BenchmarkRun benchmarkRun) { this.benchmarkRun = benchmarkRun; }
    public Long getCpuTimeMs() { return cpuTimeMs; }
    public void setCpuTimeMs(Long cpuTimeMs) { this.cpuTimeMs = cpuTimeMs; }
    public Long getWallTimeMs() { return wallTimeMs; }
    public void setWallTimeMs(Long wallTimeMs) { this.wallTimeMs = wallTimeMs; }
    public Double getPeakMemoryMb() { return peakMemoryMb; }
    public void setPeakMemoryMb(Double peakMemoryMb) { this.peakMemoryMb = peakMemoryMb; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public Boolean getTimedOut() { return timedOut; }
    public void setTimedOut(Boolean timedOut) { this.timedOut = timedOut; }
    public Long getCompileMs() { return compileMs; }
    public void setCompileMs(Long compileMs) { this.compileMs = compileMs; }
}
