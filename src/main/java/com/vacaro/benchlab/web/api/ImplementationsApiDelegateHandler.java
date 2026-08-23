package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.domain.ImplementationLanguage;
import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.CreateImplementationRequest;
import com.vacaro.benchlab.service.api.dto.ImplementationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ImplementationsApiDelegateHandler implements ImplementationsApiDelegate {

    private final BenchmarkService benchmarkService;

    public ImplementationsApiDelegateHandler(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public ResponseEntity<ImplementationResponse> createImplementation(CreateImplementationRequest createImplementationRequest) {
        var saved = benchmarkService.createImplementation(
            new com.vacaro.benchlab.service.dto.benchmark.CreateImplementationRequest(
                createImplementationRequest.getAlgorithmId(),
                ImplementationLanguage.valueOf(createImplementationRequest.getLanguage().getValue()),
                createImplementationRequest.getSourceCode(),
                createImplementationRequest.getCompileConfig(),
                createImplementationRequest.getRuntimeConfig()
            )
        );

        return ResponseEntity.ok(
            new ImplementationResponse()
                .id(saved.id())
                .algorithmId(saved.algorithmId())
                .language(saved.language())
                .implementationHash(saved.implementationHash())
        );
    }
}
