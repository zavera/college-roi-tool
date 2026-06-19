package com.example.collegeroitool.model;

import jakarta.persistence.*;

@Entity
@Table(name = "student_institutions")
public class StudentInstitution {

    @EmbeddedId
    private StudentInstitutionId id;

    private boolean active = true;

    public StudentInstitution() {}

    public StudentInstitution(StudentInstitutionId id) {
        this.id = id;
    }

    public StudentInstitutionId getId()             { return id; }
    public void setId(StudentInstitutionId id)      { this.id = id; }
    public boolean isActive()                       { return active; }
    public void setActive(boolean active)           { this.active = active; }
}
