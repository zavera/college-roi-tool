package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Coa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CoaRepository extends JpaRepository<Coa, Long> {
    List<Coa> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
