package com.example.collegeroitool.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserInstitutionId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "institution_id")
    private Long institutionId;

    public UserInstitutionId() {}

    public UserInstitutionId(Long userId, Long institutionId) {
        this.userId = userId;
        this.institutionId = institutionId;
    }

    public Long getUserId()                        { return userId; }
    public void setUserId(Long userId)             { this.userId = userId; }
    public Long getInstitutionId()                 { return institutionId; }
    public void setInstitutionId(Long id)          { this.institutionId = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInstitutionId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(institutionId, that.institutionId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, institutionId); }
}
