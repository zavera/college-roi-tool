package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scholarship_profiles")
public class ScholarshipProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String gpa;
    private String major;

    @Column(name = "state_of_residency")
    private String stateOfResidency;

    @Column(name = "citizenship_status")
    private String citizenshipStatus;

    private String ethnicity;

    @Column(name = "first_generation", nullable = false)
    private boolean firstGeneration = false;

    @Column(columnDefinition = "TEXT")
    private String extracurriculars;

    @Column(name = "target_schools_json", columnDefinition = "TEXT")
    private String targetSchoolsJson;

    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId()                              { return id; }
    public Long getUserId()                          { return userId; }
    public void setUserId(Long v)                    { this.userId = v; }
    public String getGpa()                           { return gpa; }
    public void setGpa(String v)                     { this.gpa = v; }
    public String getMajor()                         { return major; }
    public void setMajor(String v)                   { this.major = v; }
    public String getStateOfResidency()              { return stateOfResidency; }
    public void setStateOfResidency(String v)        { this.stateOfResidency = v; }
    public String getCitizenshipStatus()             { return citizenshipStatus; }
    public void setCitizenshipStatus(String v)       { this.citizenshipStatus = v; }
    public String getEthnicity()                     { return ethnicity; }
    public void setEthnicity(String v)               { this.ethnicity = v; }
    public boolean isFirstGeneration()               { return firstGeneration; }
    public void setFirstGeneration(boolean v)        { this.firstGeneration = v; }
    public String getExtracurriculars()              { return extracurriculars; }
    public void setExtracurriculars(String v)        { this.extracurriculars = v; }
    public String getTargetSchoolsJson()             { return targetSchoolsJson; }
    public void setTargetSchoolsJson(String v)       { this.targetSchoolsJson = v; }
    public String getAdditionalNotes()               { return additionalNotes; }
    public void setAdditionalNotes(String v)         { this.additionalNotes = v; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public LocalDateTime getUpdatedAt()              { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)        { this.updatedAt = v; }
}
