package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.DocumentKvExtract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentKvExtractRepository extends JpaRepository<DocumentKvExtract, Long> {
    Optional<DocumentKvExtract> findByDocumentId(Long documentId);
}
