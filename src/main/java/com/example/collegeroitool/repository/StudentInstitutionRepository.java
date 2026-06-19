package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.StudentInstitution;
import com.example.collegeroitool.model.StudentInstitutionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentInstitutionRepository extends JpaRepository<StudentInstitution, StudentInstitutionId> {
    List<StudentInstitution> findByIdInstitutionId(Long institutionId);
    List<StudentInstitution> findByIdStudentId(Long studentId);
}
