package com.vacaro.benchlab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "implementation")
public class Implementation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "algorithm_id", nullable = false)
    private Algorithm algorithm;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private ImplementationLanguage language;

    @NotBlank
    @Column(name = "source_code", nullable = false)
    private String sourceCode;

    @Column(name = "compile_config")
    private String compileConfig;

    @Column(name = "runtime_config")
    private String runtimeConfig;

    @NotBlank
    @Column(name = "implementation_hash", nullable = false)
    private String implementationHash;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public ImplementationLanguage getLanguage() {
        return language;
    }

    public void setLanguage(ImplementationLanguage language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getCompileConfig() {
        return compileConfig;
    }

    public void setCompileConfig(String compileConfig) {
        this.compileConfig = compileConfig;
    }

    public String getRuntimeConfig() {
        return runtimeConfig;
    }

    public void setRuntimeConfig(String runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public String getImplementationHash() {
        return implementationHash;
    }

    public void setImplementationHash(String implementationHash) {
        this.implementationHash = implementationHash;
    }
}
