package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.ScholarshipProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScholarshipProfileRepository extends JpaRepository<ScholarshipProfile, Long> {
    List<ScholarshipProfile> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ScholarshipProfile> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
