package com.vacaro.benchlab.service.dto.benchmark;

public record CreateAlgorithmRequest(String name, String category, String version, String complexityDeclared) {}
