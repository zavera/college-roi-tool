package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Exemption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExemptionRepository extends JpaRepository<Exemption, Long> {
    Optional<Exemption> findByUserId(Long userId);
}
