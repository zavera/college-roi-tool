package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Fafsa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FafsaRepository extends JpaRepository<Fafsa, Long> {
    List<Fafsa> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Fafsa> findByIdAndUserId(Long id, Long userId);
}
