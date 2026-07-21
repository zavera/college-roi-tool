package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.LowEarningSchoolEarnings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LowEarningSchoolEarningsRepository extends JpaRepository<LowEarningSchoolEarnings, Long> {

    Optional<LowEarningSchoolEarnings> findByNormalizedName(String normalizedName);

    Optional<LowEarningSchoolEarnings> findByUnitId(Integer unitId);

    List<LowEarningSchoolEarnings> findByNormalizedNameContaining(String token);
}
