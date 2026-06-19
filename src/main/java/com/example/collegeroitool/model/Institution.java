package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "institutions")
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                       { return id; }
    public String getName()                   { return name; }
    public void setName(String name)          { this.name = name; }
    public String getCode()                   { return code; }
    public void setCode(String code)          { this.code = code; }
    public boolean isActive()                 { return active; }
    public void setActive(boolean active)     { this.active = active; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
}
