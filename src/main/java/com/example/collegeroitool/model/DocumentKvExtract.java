package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_kv_extracts")
public class DocumentKvExtract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true)
    private Long documentId;

    @Column(name = "kv_json", nullable = false, columnDefinition = "TEXT")
    private String kvJson = "{}";

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getKvJson() { return kvJson; }
    public void setKvJson(String kvJson) { this.kvJson = kvJson; }
    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; }
}
