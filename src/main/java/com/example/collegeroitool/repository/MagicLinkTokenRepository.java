package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.MagicLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {
    Optional<MagicLinkToken> findByToken(String token);
}
