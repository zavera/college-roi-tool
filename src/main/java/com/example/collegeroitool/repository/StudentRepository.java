package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirth(
        Long userId, String firstName, String lastName, LocalDate dateOfBirth);
}
