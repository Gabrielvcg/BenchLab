package com.vacaro.benchlab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "algorithm")
public class Algorithm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @NotBlank
    @Column(name = "category", nullable = false)
    private String category;

    @NotBlank
    @Column(name = "version", nullable = false)
    private String version;

    @NotNull
    @Column(name = "complexity_declared", nullable = false)
    private String complexityDeclared;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getComplexityDeclared() {
        return complexityDeclared;
    }

    public void setComplexityDeclared(String complexityDeclared) {
        this.complexityDeclared = complexityDeclared;
    }
}
