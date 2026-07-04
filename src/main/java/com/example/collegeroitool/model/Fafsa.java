package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fafsa")
public class Fafsa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "input_fafsa_payload", columnDefinition = "TEXT")
    private String inputFafsaPayload;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                          { return id; }
    public Long getUserId()                      { return userId; }
    public void setUserId(Long v)                { this.userId = v; }
    public String getInputFafsaPayload()         { return inputFafsaPayload; }
    public void   setInputFafsaPayload(String v) { this.inputFafsaPayload = v; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
}
