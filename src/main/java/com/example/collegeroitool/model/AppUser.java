package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
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

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

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
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
}
