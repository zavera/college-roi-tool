package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private boolean active = true;

    private String passwordHash;

    /** "local" or "google" */
    @Column(nullable = false)
    private String provider = "local";

    private boolean subscriptionActive = false;

    private int searchCount = 0;

    private int debtSearchCount = 0;

    private int fafsaUsageCount = 0;

    @Column(name = "scholarship_search_count")
    private int scholarshipSearchCount = 0;

    @Column(name = "award_assist_search_count")
    private int awardAssistSearchCount = 0;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                              { return id; }
    public String getFirstName()                     { return firstName; }
    public void   setFirstName(String v)             { this.firstName = v; }
    public String getLastName()                      { return lastName; }
    public void   setLastName(String v)              { this.lastName = v; }
    public boolean isActive()                        { return active; }
    public void    setActive(boolean v)              { this.active = v; }
    public String getEmail()                         { return email; }
    public void   setEmail(String email)       { this.email = email; }
    public String getName()                    { return name; }
    public void   setName(String name)         { this.name = name; }
    public String getPasswordHash()            { return passwordHash; }
    public void   setPasswordHash(String h)    { this.passwordHash = h; }
    public String getProvider()                { return provider; }
    public void   setProvider(String p)        { this.provider = p; }
    public boolean isSubscriptionActive()      { return subscriptionActive; }
    public void    setSubscriptionActive(boolean b) { this.subscriptionActive = b; }
    public int  getSearchCount()               { return searchCount; }
    public void setSearchCount(int n)          { this.searchCount = n; }
    public int  getDebtSearchCount()           { return debtSearchCount; }
    public void setDebtSearchCount(int n)      { this.debtSearchCount = n; }
    public int  getFafsaUsageCount()             { return fafsaUsageCount; }
    public void setFafsaUsageCount(int n)        { this.fafsaUsageCount = n; }
    public int  getScholarshipSearchCount()      { return scholarshipSearchCount; }
    public void setScholarshipSearchCount(int n) { this.scholarshipSearchCount = n; }
    public int  getAwardAssistSearchCount()      { return awardAssistSearchCount; }
    public void setAwardAssistSearchCount(int n) { this.awardAssistSearchCount = n; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
}
