package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "startup")
public class Startup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // No @Lob: see Fafsa.java — @Lob on a String forces the Postgres Large Object API, which
    // requires a non-autocommit connection.
    @Column(name = "input_startup_payload", columnDefinition = "TEXT")
    private String inputStartupPayload;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                              { return id; }
    public Long getUserId()                          { return userId; }
    public void setUserId(Long v)                    { this.userId = v; }
    public String getInputStartupPayload()           { return inputStartupPayload; }
    public void   setInputStartupPayload(String v)   { this.inputStartupPayload = v; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
}
