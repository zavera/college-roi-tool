package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.FafsaHandbookReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FafsaHandbookReferenceRepository extends JpaRepository<FafsaHandbookReference, Long> {
    List<FafsaHandbookReference> findByAwardYearOrderById(String awardYear);
    Optional<FafsaHandbookReference> findByTopicAndAwardYear(String topic, String awardYear);
}
