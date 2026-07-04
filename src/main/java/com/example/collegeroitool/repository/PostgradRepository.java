package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Postgrad;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostgradRepository extends JpaRepository<Postgrad, Long> {
    List<Postgrad> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
