package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.SearchUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SearchUsageRepository extends JpaRepository<SearchUsage, Long> {
    Optional<SearchUsage> findByUserId(Long userId);
}
