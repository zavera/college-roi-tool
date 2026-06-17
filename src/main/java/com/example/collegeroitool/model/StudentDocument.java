package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_documents")
public class StudentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "blob_name", nullable = false)
    private String blobName;

    @Column(name = "blob_url", nullable = false)
    private String blobUrl;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getBlobName() { return blobName; }
    public void setBlobName(String blobName) { this.blobName = blobName; }
    public String getBlobUrl() { return blobUrl; }
    public void setBlobUrl(String blobUrl) { this.blobUrl = blobUrl; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
