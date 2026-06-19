package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fafsa_aid_packages",
    uniqueConstraints = @UniqueConstraint(name = "uq_fafsa_aid_student_year",
        columnNames = {"student_id", "aid_year"}))
public class FafsaAidPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "aid_year", nullable = false)
    private Integer aidYear;

    @Column(name = "college_name")
    private String collegeName;

    private String major;
    private String residency;

    @Column(name = "living_situation")
    private String livingSituation;

    @Column(name = "cost_of_attendance", precision = 12, scale = 2)
    private BigDecimal costOfAttendance;

    @Column(name = "net_price", precision = 12, scale = 2)
    private BigDecimal netPrice;

    @Column(name = "unmet_need", precision = 12, scale = 2)
    private BigDecimal unmetNeed;

    @Column(name = "pell_grant", precision = 12, scale = 2)
    private BigDecimal pellGrant;

    @Column(name = "institutional_grant", precision = 12, scale = 2)
    private BigDecimal institutionalGrant;

    @Column(name = "subsidized_loan", precision = 12, scale = 2)
    private BigDecimal subsidizedLoan;

    @Column(name = "unsubsidized_loan", precision = 12, scale = 2)
    private BigDecimal unsubsidizedLoan;

    @Column(name = "parent_plus_loan", precision = 12, scale = 2)
    private BigDecimal parentPlusLoan;

    @Column(name = "private_loan", precision = 12, scale = 2)
    private BigDecimal privateLoan;

    @Column(name = "scholarship_amount", precision = 12, scale = 2)
    private BigDecimal scholarshipAmount;

    @Column(name = "work_study", precision = 12, scale = 2)
    private BigDecimal workStudy;

    @Column(name = "six_yr_earnings", precision = 12, scale = 2)
    private BigDecimal sixYrEarnings;

    @Column(name = "college_wide_earnings", precision = 12, scale = 2)
    private BigDecimal collegeWideEarnings;

    @Column(name = "efc_sai", precision = 12, scale = 2)
    private BigDecimal efcSai;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId()                                  { return id; }
    public Long getStudentId()                           { return studentId; }
    public void setStudentId(Long v)                     { this.studentId = v; }
    public Integer getAidYear()                          { return aidYear; }
    public void setAidYear(Integer v)                    { this.aidYear = v; }
    public String getCollegeName()                       { return collegeName; }
    public void setCollegeName(String v)                 { this.collegeName = v; }
    public String getMajor()                             { return major; }
    public void setMajor(String v)                       { this.major = v; }
    public String getResidency()                         { return residency; }
    public void setResidency(String v)                   { this.residency = v; }
    public String getLivingSituation()                   { return livingSituation; }
    public void setLivingSituation(String v)             { this.livingSituation = v; }
    public BigDecimal getCostOfAttendance()              { return costOfAttendance; }
    public void setCostOfAttendance(BigDecimal v)        { this.costOfAttendance = v; }
    public BigDecimal getNetPrice()                      { return netPrice; }
    public void setNetPrice(BigDecimal v)                { this.netPrice = v; }
    public BigDecimal getUnmetNeed()                     { return unmetNeed; }
    public void setUnmetNeed(BigDecimal v)               { this.unmetNeed = v; }
    public BigDecimal getPellGrant()                     { return pellGrant; }
    public void setPellGrant(BigDecimal v)               { this.pellGrant = v; }
    public BigDecimal getInstitutionalGrant()            { return institutionalGrant; }
    public void setInstitutionalGrant(BigDecimal v)      { this.institutionalGrant = v; }
    public BigDecimal getSubsidizedLoan()                { return subsidizedLoan; }
    public void setSubsidizedLoan(BigDecimal v)          { this.subsidizedLoan = v; }
    public BigDecimal getUnsubsidizedLoan()              { return unsubsidizedLoan; }
    public void setUnsubsidizedLoan(BigDecimal v)        { this.unsubsidizedLoan = v; }
    public BigDecimal getParentPlusLoan()                { return parentPlusLoan; }
    public void setParentPlusLoan(BigDecimal v)          { this.parentPlusLoan = v; }
    public BigDecimal getPrivateLoan()                   { return privateLoan; }
    public void setPrivateLoan(BigDecimal v)             { this.privateLoan = v; }
    public BigDecimal getScholarshipAmount()             { return scholarshipAmount; }
    public void setScholarshipAmount(BigDecimal v)       { this.scholarshipAmount = v; }
    public BigDecimal getWorkStudy()                     { return workStudy; }
    public void setWorkStudy(BigDecimal v)               { this.workStudy = v; }
    public BigDecimal getSixYrEarnings()                 { return sixYrEarnings; }
    public void setSixYrEarnings(BigDecimal v)           { this.sixYrEarnings = v; }
    public BigDecimal getCollegeWideEarnings()           { return collegeWideEarnings; }
    public void setCollegeWideEarnings(BigDecimal v)     { this.collegeWideEarnings = v; }
    public BigDecimal getEfcSai()                        { return efcSai; }
    public void setEfcSai(BigDecimal v)                  { this.efcSai = v; }
    public LocalDateTime getCreatedAt()                  { return createdAt; }
    public LocalDateTime getUpdatedAt()                  { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)            { this.updatedAt = v; }
}
