package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {
    List<StudentDocument> findByStudentIdAndActiveTrue(Long studentId);
    List<StudentDocument> findByStudentId(Long studentId);
}
