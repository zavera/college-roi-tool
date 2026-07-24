package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Startup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StartupRepository extends JpaRepository<Startup, Long> {
    List<Startup> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
