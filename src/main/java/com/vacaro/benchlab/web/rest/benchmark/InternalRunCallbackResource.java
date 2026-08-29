package com.vacaro.benchlab.web.rest.benchmark;

import com.vacaro.benchlab.config.BenchLabProperties;
import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.dto.benchmark.RunResultCallbackRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/runs")
public class InternalRunCallbackResource {

    private static final String WORKER_TOKEN_HEADER = "X-Worker-Token";

    private final BenchmarkService benchmarkService;
    private final BenchLabProperties benchLabProperties;

    public InternalRunCallbackResource(BenchmarkService benchmarkService, BenchLabProperties benchLabProperties) {
        this.benchmarkService = benchmarkService;
        this.benchLabProperties = benchLabProperties;
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> markStarted(
        @PathVariable("id") Long runId,
        @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
        @RequestHeader("X-Runner-Host") String runnerHost
    ) {
        authorize(workerToken);
        benchmarkService.markRunStarted(runId, runnerHost);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/result")
    public ResponseEntity<Void> registerResult(
        @PathVariable("id") Long runId,
        @Valid @RequestBody RunResultCallbackRequest request,
        @RequestHeader(value = WORKER_TOKEN_HEADER, required = false) String workerToken
    ) {
        authorize(workerToken);
        benchmarkService.registerRunResult(runId, request);
        return ResponseEntity.noContent().build();
    }

    private void authorize(String workerToken) {
        String expectedToken = benchLabProperties.getWorker().getCallbackToken();
        if (
            workerToken == null ||
            expectedToken == null ||
            !MessageDigest.isEqual(workerToken.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8))
        ) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid worker token");
        }
    }
}
