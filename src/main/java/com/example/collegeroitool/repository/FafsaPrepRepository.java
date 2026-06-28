package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.FafsaPrepEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FafsaPrepRepository extends JpaRepository<FafsaPrepEntry, Long> {
    List<FafsaPrepEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<FafsaPrepEntry> findByIdAndUserId(Long id, Long userId);
}
