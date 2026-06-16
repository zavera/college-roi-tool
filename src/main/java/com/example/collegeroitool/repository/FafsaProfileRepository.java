package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.FafsaProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FafsaProfileRepository extends JpaRepository<FafsaProfile, Long> {
    Optional<FafsaProfile> findByUser_Email(String email);
}
