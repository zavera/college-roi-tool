package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.UserInstitution;
import com.example.collegeroitool.model.UserInstitutionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserInstitutionRepository extends JpaRepository<UserInstitution, UserInstitutionId> {
    List<UserInstitution> findByIdUserId(Long userId);
}
