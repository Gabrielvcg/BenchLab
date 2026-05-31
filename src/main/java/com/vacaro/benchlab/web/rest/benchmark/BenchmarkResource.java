package com.vacaro.benchlab.web.rest.benchmark;

import com.vacaro.benchlab.config.BenchLabProperties;
import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.dto.benchmark.AlgorithmResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkCompareResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkTimeseriesResponse;
import com.vacaro.benchlab.service.dto.benchmark.CreateAlgorithmRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateImplementationRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateRunRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateDatasetRequest;
import com.vacaro.benchlab.service.dto.benchmark.DatasetResponse;
import com.vacaro.benchlab.service.dto.benchmark.ImplementationResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResultCallbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BenchmarkResource {

    private final BenchmarkService benchmarkService;
    private final BenchLabProperties benchLabProperties;

    public BenchmarkResource(BenchmarkService benchmarkService, BenchLabProperties benchLabProperties) {
        this.benchmarkService = benchmarkService;
        this.benchLabProperties = benchLabProperties;
    }

    @PostMapping("/implementations")
    public ResponseEntity<ImplementationResponse> createImplementation(@Valid @RequestBody CreateImplementationRequest request) {
        return ResponseEntity.ok(benchmarkService.createImplementation(request));
    }

    @PostMapping("/algorithms")
    public ResponseEntity<AlgorithmResponse> createAlgorithm(@Valid @RequestBody CreateAlgorithmRequest request) {
        return ResponseEntity.ok(benchmarkService.createAlgorithm(request));
    }

    @PostMapping("/datasets")
    public ResponseEntity<DatasetResponse> createDataset(@Valid @RequestBody CreateDatasetRequest request) {
        return ResponseEntity.ok(benchmarkService.createDataset(request));
    }

    @PostMapping("/runs")
    public ResponseEntity<RunResponse> createRun(@Valid @RequestBody CreateRunRequest request) {
        return ResponseEntity.ok(benchmarkService.createRun(request));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<RunResponse> getRun(@PathVariable("id") Long runId) {
        return ResponseEntity.ok(benchmarkService.getRun(runId));
    }

    @PostMapping("/internal/runs/{id}/result")
    public ResponseEntity<Void> registerResult(
        @PathVariable("id") Long runId,
        @RequestBody RunResultCallbackRequest request,
        @org.springframework.web.bind.annotation.RequestHeader(value = "X-Worker-Token", required = false) String workerToken
    ) {
        if (workerToken == null || !workerToken.equals(benchLabProperties.getWorker().getCallbackToken())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid worker token");
        }
        benchmarkService.registerRunResult(runId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/benchmarks/compare")
    public ResponseEntity<BenchmarkCompareResponse> compare(@RequestParam Long algorithmId, @RequestParam Long datasetId) {
        return ResponseEntity.ok(benchmarkService.compare(algorithmId, datasetId));
    }

    @GetMapping("/benchmarks/timeseries")
    public ResponseEntity<BenchmarkTimeseriesResponse> timeseries(@RequestParam Long algorithmId, @RequestParam String language) {
        return ResponseEntity.ok(benchmarkService.timeseries(algorithmId, language));
    }
}
