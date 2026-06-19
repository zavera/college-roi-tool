package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.UserInstitution;
import com.example.collegeroitool.model.UserInstitutionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserInstitutionRepository extends JpaRepository<UserInstitution, UserInstitutionId> {
    List<UserInstitution> findByIdUserId(Long userId);

    /** True when a user has at least one active membership at a non-default institution. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT COUNT(ui) > 0 FROM UserInstitution ui
        JOIN Institution i ON i.id = ui.id.institutionId
        WHERE ui.id.userId = :userId
          AND ui.active = true
          AND i.code <> 'callisto-tech'
        """)
    boolean hasInstitutionAccess(@org.springframework.data.repository.query.Param("userId") Long userId);
}
