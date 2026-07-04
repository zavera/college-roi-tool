package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_usages")
public class SearchUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    private int fafsa = 0;
    private int scholarship = 0;
    private int coa = 0;
    private int postgrad = 0;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId()                     { return id; }
    public Long getUserId()                 { return userId; }
    public void setUserId(Long v)           { this.userId = v; }
    public int  getFafsa()                  { return fafsa; }
    public void setFafsa(int v)             { this.fafsa = v; }
    public int  getScholarship()            { return scholarship; }
    public void setScholarship(int v)       { this.scholarship = v; }
    public int  getCoa()                    { return coa; }
    public void setCoa(int v)               { this.coa = v; }
    public int  getPostgrad()               { return postgrad; }
    public void setPostgrad(int v)          { this.postgrad = v; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
    public LocalDateTime getUpdatedAt()     { return updatedAt; }
}
