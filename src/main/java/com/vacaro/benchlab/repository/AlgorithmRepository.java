package com.vacaro.benchlab.repository;

import com.vacaro.benchlab.domain.Algorithm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlgorithmRepository extends JpaRepository<Algorithm, Long> {
    Optional<Algorithm> findByName(String name);
}
