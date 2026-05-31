package com.vacaro.benchlab.service.dto.benchmark;

import com.vacaro.benchlab.domain.ImplementationLanguage;

public record CreateImplementationRequest(
    Long algorithmId,
    ImplementationLanguage language,
    String sourceCode,
    String compileConfig,
    String runtimeConfig
) {}
