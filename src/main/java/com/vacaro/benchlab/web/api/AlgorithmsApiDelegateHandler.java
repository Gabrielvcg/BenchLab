package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.AlgorithmResponse;
import com.vacaro.benchlab.service.api.dto.CreateAlgorithmRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AlgorithmsApiDelegateHandler implements AlgorithmsApiDelegate {

    private final BenchmarkService benchmarkService;

    public AlgorithmsApiDelegateHandler(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public ResponseEntity<AlgorithmResponse> createAlgorithm(CreateAlgorithmRequest createAlgorithmRequest) {
        var saved = benchmarkService.createAlgorithm(
            new com.vacaro.benchlab.service.dto.benchmark.CreateAlgorithmRequest(
                createAlgorithmRequest.getName(),
                createAlgorithmRequest.getCategory(),
                createAlgorithmRequest.getVersion(),
                createAlgorithmRequest.getComplexityDeclared()
            )
        );
        return ResponseEntity.ok(toAlgorithmResponse(saved));
    }

    @Override
    public ResponseEntity<List<AlgorithmResponse>> listAlgorithms() {
        return ResponseEntity.ok(
            benchmarkService.listAlgorithms().stream().map(AlgorithmsApiDelegateHandler::toAlgorithmResponse).toList()
        );
    }

    private static AlgorithmResponse toAlgorithmResponse(com.vacaro.benchlab.service.dto.benchmark.AlgorithmResponse algorithm) {
        return new AlgorithmResponse()
            .id(algorithm.id())
            .name(algorithm.name())
            .category(algorithm.category())
            .version(algorithm.version())
            .complexityDeclared(algorithm.complexityDeclared());
    }
}
