package com.example.collegeroitool.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudentInstitutionId implements Serializable {

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "institution_id")
    private Long institutionId;

    public StudentInstitutionId() {}

    public StudentInstitutionId(Long studentId, Long institutionId) {
        this.studentId = studentId;
        this.institutionId = institutionId;
    }

    public Long getStudentId()                     { return studentId; }
    public void setStudentId(Long studentId)       { this.studentId = studentId; }
    public Long getInstitutionId()                 { return institutionId; }
    public void setInstitutionId(Long id)          { this.institutionId = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudentInstitutionId that)) return false;
        return Objects.equals(studentId, that.studentId) && Objects.equals(institutionId, that.institutionId);
    }

    @Override
    public int hashCode() { return Objects.hash(studentId, institutionId); }
}
