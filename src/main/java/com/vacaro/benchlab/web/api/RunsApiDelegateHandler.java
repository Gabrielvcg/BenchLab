package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.CreateRunRequest;
import com.vacaro.benchlab.service.api.dto.RunArtifactResponse;
import com.vacaro.benchlab.service.api.dto.RunMetricResponse;
import com.vacaro.benchlab.service.api.dto.RunResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class RunsApiDelegateHandler implements RunsApiDelegate {

    private final BenchmarkService benchmarkService;

    public RunsApiDelegateHandler(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public ResponseEntity<RunResponse> createRun(CreateRunRequest createRunRequest) {
        var saved = benchmarkService.createRun(
            new com.vacaro.benchlab.service.dto.benchmark.CreateRunRequest(
                createRunRequest.getImplementationId(),
                createRunRequest.getDatasetId(),
                createRunRequest.getTimeoutMs(),
                createRunRequest.getMemoryMb(),
                createRunRequest.getCpuLimit(),
                createRunRequest.getIterations()
            )
        );
        return ResponseEntity.ok(toRunResponse(saved));
    }

    @Override
    public ResponseEntity<RunResponse> getRun(Long id) {
        return ResponseEntity.ok(toRunResponse(benchmarkService.getRun(id)));
    }

    static RunResponse toRunResponse(com.vacaro.benchlab.service.dto.benchmark.RunResponse run) {
        RunMetricResponse metric = null;
        if (run.metric() != null) {
            metric = new RunMetricResponse()
                .cpuTimeMs(run.metric().cpuTimeMs())
                .wallTimeMs(run.metric().wallTimeMs())
                .peakMemoryMb(run.metric().peakMemoryMb())
                .exitCode(run.metric().exitCode())
                .timedOut(run.metric().timedOut())
                .compileMs(run.metric().compileMs());
        }

        RunArtifactResponse artifact = null;
        if (run.artifact() != null) {
            artifact = new RunArtifactResponse()
                .stdoutTruncated(run.artifact().stdoutTruncated())
                .stderrTruncated(run.artifact().stderrTruncated())
                .outputSizeBytes(run.artifact().outputSizeBytes())
                .artifactChecksum(run.artifact().artifactChecksum())
                .technicalLogSummary(run.artifact().technicalLogSummary());
        }

        return new RunResponse()
            .id(run.id())
            .status(run.status())
            .traceId(run.traceId())
            .queuedAt(run.queuedAt())
            .startedAt(run.startedAt())
            .finishedAt(run.finishedAt())
            .failureReason(run.failureReason())
            .metric(metric)
            .artifact(artifact);
    }
}
