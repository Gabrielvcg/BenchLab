package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.CreateRunRequest;
import com.vacaro.benchlab.service.api.dto.RunArtifactResponse;
import com.vacaro.benchlab.service.api.dto.RunMetricResponse;
import com.vacaro.benchlab.service.api.dto.RunResponse;
import com.vacaro.benchlab.service.api.dto.RunSummaryResponse;
import java.util.List;
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
                createRunRequest.getIterations(),
                createRunRequest.getWarmupIterations()
            )
        );
        return ResponseEntity.ok(toRunResponse(saved));
    }

    @Override
    public ResponseEntity<RunResponse> getRun(Long id) {
        return ResponseEntity.ok(toRunResponse(benchmarkService.getRun(id)));
    }

    @Override
    public ResponseEntity<List<RunSummaryResponse>> listRecentRuns() {
        return ResponseEntity.ok(
            benchmarkService
                .listRecentRuns()
                .stream()
                .map(run ->
                    new RunSummaryResponse()
                        .id(run.id())
                        .status(run.status())
                        .language(run.language())
                        .algorithmId(run.algorithmId())
                        .algorithmName(run.algorithmName())
                        .datasetId(run.datasetId())
                        .datasetSize(run.datasetSize())
                        .queuedAt(run.queuedAt())
                        .finishedAt(run.finishedAt())
                        .cpuTimeMs(run.cpuTimeMs())
                        .orchestrationWallTimeMs(run.orchestrationWallTimeMs())
                        .executionWallTimeMs(run.executionWallTimeMs())
                )
                .toList()
        );
    }

    static RunResponse toRunResponse(com.vacaro.benchlab.service.dto.benchmark.RunResponse run) {
        RunMetricResponse metric = null;
        if (run.metric() != null) {
            metric = new RunMetricResponse()
                .cpuTimeMs(run.metric().cpuTimeMs())
                .orchestrationWallTimeMs(run.metric().orchestrationWallTimeMs())
                .executionWallTimeMs(run.metric().executionWallTimeMs())
                .peakMemoryMb(run.metric().peakMemoryMb())
                .exitCode(run.metric().exitCode())
                .timedOut(run.metric().timedOut())
                .compileWallTimeMs(run.metric().compileWallTimeMs());
        }

        RunArtifactResponse artifact = null;
        if (run.artifact() != null) {
            artifact = new RunArtifactResponse()
                .stdoutPreview(run.artifact().stdoutPreview())
                .stderrPreview(run.artifact().stderrPreview())
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
