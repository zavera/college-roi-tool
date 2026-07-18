package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {
    List<TokenUsage> findByUserIdAndDateCreatedGreaterThanEqual(Long userId, LocalDateTime since);
}
