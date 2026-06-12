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

    private String passwordHash;

    /** "local" or "google" */
    @Column(nullable = false)
    private String provider = "local";

    private boolean subscriptionActive = false;

    private int searchCount = 0;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public String getEmail()                   { return email; }
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
    public LocalDateTime getCreatedAt()        { return createdAt; }
}
