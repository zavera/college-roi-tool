package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coa")
public class Coa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // No @Lob: see Fafsa.java — @Lob on a String forces the Postgres Large Object API, which
    // requires a non-autocommit connection.
    @Column(name = "input_coa_payload", columnDefinition = "TEXT")
    private String inputCoaPayload;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                        { return id; }
    public Long getUserId()                    { return userId; }
    public void setUserId(Long v)              { this.userId = v; }
    public String getInputCoaPayload()         { return inputCoaPayload; }
    public void   setInputCoaPayload(String v) { this.inputCoaPayload = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
}
