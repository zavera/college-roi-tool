package com.example.collegeroitool.service;

import com.example.collegeroitool.model.ScholarshipProfile;
import com.example.collegeroitool.repository.ScholarshipProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ScholarshipProfileService {

    private final ScholarshipProfileRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ScholarshipProfileService(ScholarshipProfileRepository repo) {
        this.repo = repo;
    }

    /** Upsert: insert on first save, update on every subsequent save for the same student. */
    public ScholarshipProfile upsert(Long studentId, Map<String, Object> demographics,
                                     String comments, List<String> targetSchools) {
        ScholarshipProfile profile = repo.findByStudentId(studentId)
            .orElseGet(() -> { ScholarshipProfile p = new ScholarshipProfile(); p.setStudentId(studentId); return p; });

        profile.setGpa(str(demographics, "gpa"));
        profile.setMajor(str(demographics, "major"));
        profile.setStateOfResidency(str(demographics, "state"));
        profile.setCitizenshipStatus(str(demographics, "citizenship"));
        profile.setEthnicity(str(demographics, "ethnicity"));
        profile.setFirstGeneration(Boolean.TRUE.equals(demographics.get("firstGen")));
        profile.setExtracurriculars(str(demographics, "extracurriculars"));
        profile.setAdditionalNotes(comments);

        if (targetSchools != null && !targetSchools.isEmpty()) {
            try { profile.setTargetSchoolsJson(mapper.writeValueAsString(targetSchools)); }
            catch (Exception ignored) {}
        }

        profile.setUpdatedAt(LocalDateTime.now());
        return repo.save(profile);
    }

    public Optional<ScholarshipProfile> findByStudentId(Long studentId) {
        return repo.findByStudentId(studentId);
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
