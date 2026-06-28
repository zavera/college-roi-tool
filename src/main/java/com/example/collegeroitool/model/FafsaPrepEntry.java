package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fafsa_prep_entries")
public class FafsaPrepEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    private String addedBy;
    private String label;
    private Integer planningYear;
    private String dependencyStatus;

    // Student income
    private BigDecimal studentAgi;
    private BigDecimal studentTaxesPaid;
    private BigDecimal studentUntaxedIncome;
    private BigDecimal studentWorkStudy;

    // Student assets
    private BigDecimal studentCashSavings;
    private BigDecimal studentInvestments;
    private BigDecimal studentBusinessNetWorth;

    // Household
    private Integer householdSize;
    private Integer numberInCollege;

    // Parent income
    private BigDecimal parentAgi;
    private BigDecimal parentTaxesPaid;
    private BigDecimal parentUntaxedIncome;
    private String parentMaritalStatus;
    private Integer parentAge;

    // Parent assets
    private BigDecimal parentCashSavings;
    private BigDecimal parentInvestments;
    private BigDecimal parentHomeEquity;
    private BigDecimal parentRetirementSavings;
    private BigDecimal parentBusinessNetWorth;
    private BigDecimal parent529Balance;

    // Computed / AI
    private BigDecimal estimatedSai;

    @Lob @Column(columnDefinition = "TEXT")
    private String assetRepositioningJson;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Getters / Setters ────────────────────────────────────────────────────
    public Long getId()                                   { return id; }
    public AppUser getUser()                              { return user; }
    public void setUser(AppUser u)                        { this.user = u; }
    public String getAddedBy()                            { return addedBy; }
    public void setAddedBy(String v)                      { this.addedBy = v; }
    public String getLabel()                              { return label; }
    public void setLabel(String v)                        { this.label = v; }
    public Integer getPlanningYear()                      { return planningYear; }
    public void setPlanningYear(Integer v)                { this.planningYear = v; }
    public String getDependencyStatus()                   { return dependencyStatus; }
    public void setDependencyStatus(String v)             { this.dependencyStatus = v; }
    public BigDecimal getStudentAgi()                     { return studentAgi; }
    public void setStudentAgi(BigDecimal v)               { this.studentAgi = v; }
    public BigDecimal getStudentTaxesPaid()               { return studentTaxesPaid; }
    public void setStudentTaxesPaid(BigDecimal v)         { this.studentTaxesPaid = v; }
    public BigDecimal getStudentUntaxedIncome()           { return studentUntaxedIncome; }
    public void setStudentUntaxedIncome(BigDecimal v)     { this.studentUntaxedIncome = v; }
    public BigDecimal getStudentWorkStudy()               { return studentWorkStudy; }
    public void setStudentWorkStudy(BigDecimal v)         { this.studentWorkStudy = v; }
    public BigDecimal getStudentCashSavings()             { return studentCashSavings; }
    public void setStudentCashSavings(BigDecimal v)       { this.studentCashSavings = v; }
    public BigDecimal getStudentInvestments()             { return studentInvestments; }
    public void setStudentInvestments(BigDecimal v)       { this.studentInvestments = v; }
    public BigDecimal getStudentBusinessNetWorth()        { return studentBusinessNetWorth; }
    public void setStudentBusinessNetWorth(BigDecimal v)  { this.studentBusinessNetWorth = v; }
    public Integer getHouseholdSize()                     { return householdSize; }
    public void setHouseholdSize(Integer v)               { this.householdSize = v; }
    public Integer getNumberInCollege()                   { return numberInCollege; }
    public void setNumberInCollege(Integer v)             { this.numberInCollege = v; }
    public BigDecimal getParentAgi()                      { return parentAgi; }
    public void setParentAgi(BigDecimal v)                { this.parentAgi = v; }
    public BigDecimal getParentTaxesPaid()                { return parentTaxesPaid; }
    public void setParentTaxesPaid(BigDecimal v)          { this.parentTaxesPaid = v; }
    public BigDecimal getParentUntaxedIncome()            { return parentUntaxedIncome; }
    public void setParentUntaxedIncome(BigDecimal v)      { this.parentUntaxedIncome = v; }
    public String getParentMaritalStatus()                { return parentMaritalStatus; }
    public void setParentMaritalStatus(String v)          { this.parentMaritalStatus = v; }
    public Integer getParentAge()                         { return parentAge; }
    public void setParentAge(Integer v)                   { this.parentAge = v; }
    public BigDecimal getParentCashSavings()              { return parentCashSavings; }
    public void setParentCashSavings(BigDecimal v)        { this.parentCashSavings = v; }
    public BigDecimal getParentInvestments()              { return parentInvestments; }
    public void setParentInvestments(BigDecimal v)        { this.parentInvestments = v; }
    public BigDecimal getParentHomeEquity()               { return parentHomeEquity; }
    public void setParentHomeEquity(BigDecimal v)         { this.parentHomeEquity = v; }
    public BigDecimal getParentRetirementSavings()        { return parentRetirementSavings; }
    public void setParentRetirementSavings(BigDecimal v)  { this.parentRetirementSavings = v; }
    public BigDecimal getParentBusinessNetWorth()         { return parentBusinessNetWorth; }
    public void setParentBusinessNetWorth(BigDecimal v)   { this.parentBusinessNetWorth = v; }
    public BigDecimal getParent529Balance()               { return parent529Balance; }
    public void setParent529Balance(BigDecimal v)         { this.parent529Balance = v; }
    public BigDecimal getEstimatedSai()                   { return estimatedSai; }
    public void setEstimatedSai(BigDecimal v)             { this.estimatedSai = v; }
    public String getAssetRepositioningJson()             { return assetRepositioningJson; }
    public void setAssetRepositioningJson(String v)       { this.assetRepositioningJson = v; }
    public LocalDateTime getCreatedAt()                   { return createdAt; }
}
