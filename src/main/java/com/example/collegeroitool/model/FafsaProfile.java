package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fafsa_profiles")
public class FafsaProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    private String studentName;

    private LocalDate dateOfBirth;

    /** Year the student is planning to start/be in college */
    private Integer planningYear;

    /** Merged key-value pairs from all uploaded tax documents: {"filename.pdf": {...kv...}, ...} */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String extractedDataJson;

    /** AI output: readiness summary, aid projection, appeal opportunities, scholarship matches, deadlines */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String readinessSummaryJson;

    /** Scholarship/appeal items the student checked, used to build the roadmap */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String selectedOptionsJson;

    /** Final AI-generated roadmap */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String roadmapJson;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                                 { return id; }
    public AppUser getUser()                             { return user; }
    public void setUser(AppUser user)                    { this.user = user; }
    public String getStudentName()                       { return studentName; }
    public void setStudentName(String v)                 { this.studentName = v; }
    public LocalDate getDateOfBirth()                    { return dateOfBirth; }
    public void setDateOfBirth(LocalDate v)               { this.dateOfBirth = v; }
    public Integer getPlanningYear()                     { return planningYear; }
    public void setPlanningYear(Integer v)                { this.planningYear = v; }
    public String getExtractedDataJson()                 { return extractedDataJson; }
    public void setExtractedDataJson(String v)            { this.extractedDataJson = v; }
    public String getReadinessSummaryJson()               { return readinessSummaryJson; }
    public void setReadinessSummaryJson(String v)          { this.readinessSummaryJson = v; }
    public String getSelectedOptionsJson()                { return selectedOptionsJson; }
    public void setSelectedOptionsJson(String v)           { this.selectedOptionsJson = v; }
    public String getRoadmapJson()                        { return roadmapJson; }
    public void setRoadmapJson(String v)                  { this.roadmapJson = v; }
    public LocalDateTime getCreatedAt()                   { return createdAt; }
    public LocalDateTime getUpdatedAt()                   { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)              { this.updatedAt = v; }
}
