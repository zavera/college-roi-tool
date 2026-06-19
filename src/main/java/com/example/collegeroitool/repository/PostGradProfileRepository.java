package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.PostGradProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PostGradProfileRepository extends JpaRepository<PostGradProfile, Long> {
    Optional<PostGradProfile> findTopByStudentIdOrderByCreatedAtDesc(Long studentId);
}
