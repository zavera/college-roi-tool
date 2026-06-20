package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.FafsaAidPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FafsaAidPackageRepository extends JpaRepository<FafsaAidPackage, Long> {

    Optional<FafsaAidPackage> findByUserIdAndAidYear(Long userId, Integer aidYear);

    /** Latest row per aid year for a user — used to compute post-grad loan defaults. */
    @Query("""
        SELECT f FROM FafsaAidPackage f
        WHERE f.userId = :userId
          AND f.createdAt = (
              SELECT MAX(f2.createdAt) FROM FafsaAidPackage f2
              WHERE f2.userId = :userId AND f2.aidYear = f.aidYear
          )
        ORDER BY f.createdAt DESC
        """)
    List<FafsaAidPackage> findLatestPerAidYear(@Param("userId") Long userId);

    /** Single latest row across all aid years — used to pre-populate the aid package form. */
    List<FafsaAidPackage> findByUserIdOrderByCreatedAtDesc(Long userId);
}
