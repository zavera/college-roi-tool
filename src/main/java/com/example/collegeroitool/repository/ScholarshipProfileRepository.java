package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.ScholarshipProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScholarshipProfileRepository extends JpaRepository<ScholarshipProfile, Long> {
    Optional<ScholarshipProfile> findByStudentId(Long studentId);
}
