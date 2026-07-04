package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Scholarship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {
    List<Scholarship> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
