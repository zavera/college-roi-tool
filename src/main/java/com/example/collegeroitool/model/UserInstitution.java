package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_institutions")
public class UserInstitution {

    @EmbeddedId
    private UserInstitutionId id;

    private boolean active = true;

    /** Institution-specific email for this user — unique per institution, seeded during onboarding. */
    @Column(name = "email")
    private String email;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    public UserInstitution() {}

    public UserInstitution(UserInstitutionId id) {
        this.id = id;
    }

    public UserInstitutionId getId()              { return id; }
    public void setId(UserInstitutionId id)       { this.id = id; }
    public boolean isActive()                     { return active; }
    public void setActive(boolean active)         { this.active = active; }
    public String getEmail()                      { return email; }
    public void setEmail(String email)            { this.email = email; }
    public LocalDateTime getJoinedAt()            { return joinedAt; }
}
