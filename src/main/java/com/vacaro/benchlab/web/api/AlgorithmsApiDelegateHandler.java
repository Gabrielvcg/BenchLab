package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.CreateAlgorithmRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AlgorithmsApiDelegateHandler implements AlgorithmsApiDelegate {

    private final BenchmarkService benchmarkService;

    public AlgorithmsApiDelegateHandler(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public ResponseEntity<com.vacaro.benchlab.service.api.dto.AlgorithmResponse> createAlgorithm(CreateAlgorithmRequest createAlgorithmRequest) {
        var saved = benchmarkService.createAlgorithm(
            new com.vacaro.benchlab.service.dto.benchmark.CreateAlgorithmRequest(
                createAlgorithmRequest.getName(),
                createAlgorithmRequest.getCategory(),
                createAlgorithmRequest.getVersion(),
                createAlgorithmRequest.getComplexityDeclared()
            )
        );
        return ResponseEntity.ok(
            new com.vacaro.benchlab.service.api.dto.AlgorithmResponse()
                .id(saved.id())
                .name(saved.name())
                .category(saved.category())
                .version(saved.version())
                .complexityDeclared(saved.complexityDeclared())
        );
    }
}
