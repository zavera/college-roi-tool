package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_grad_profiles")
public class PostGradProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "federal_loan_balance", precision = 12, scale = 2)
    private BigDecimal federalLoanBalance;

    @Column(name = "private_loan_balance", precision = 12, scale = 2)
    private BigDecimal privateLoanBalance;

    @Column(name = "private_lender")
    private String privateLender;

    @Column(name = "loan_servicer")
    private String loanServicer;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "grace_period_end_date")
    private LocalDate gracePeriodEndDate;

    @Column(name = "employment_status")
    private String employmentStatus;

    @Column(name = "employer_name")
    private String employerName;

    @Column(name = "annual_gross_income", precision = 12, scale = 2)
    private BigDecimal annualGrossIncome;

    @Column(name = "household_size")
    private Integer householdSize;

    @Column(name = "marital_status")
    private String maritalStatus;

    @Column(name = "credit_score_band")
    private String creditScoreBand;

    @Column(name = "disability_status")
    private Boolean disabilityStatus;

    @Column(name = "school_attended")
    private String schoolAttended;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId()                                  { return id; }
    public Long getUserId()                              { return userId; }
    public void setUserId(Long v)                        { this.userId = v; }
    public BigDecimal getFederalLoanBalance()            { return federalLoanBalance; }
    public void setFederalLoanBalance(BigDecimal v)      { this.federalLoanBalance = v; }
    public BigDecimal getPrivateLoanBalance()            { return privateLoanBalance; }
    public void setPrivateLoanBalance(BigDecimal v)      { this.privateLoanBalance = v; }
    public String getPrivateLender()                     { return privateLender; }
    public void setPrivateLender(String v)               { this.privateLender = v; }
    public String getLoanServicer()                      { return loanServicer; }
    public void setLoanServicer(String v)                { this.loanServicer = v; }
    public BigDecimal getInterestRate()                  { return interestRate; }
    public void setInterestRate(BigDecimal v)            { this.interestRate = v; }
    public LocalDate getGracePeriodEndDate()             { return gracePeriodEndDate; }
    public void setGracePeriodEndDate(LocalDate v)       { this.gracePeriodEndDate = v; }
    public String getEmploymentStatus()                  { return employmentStatus; }
    public void setEmploymentStatus(String v)            { this.employmentStatus = v; }
    public String getEmployerName()                      { return employerName; }
    public void setEmployerName(String v)                { this.employerName = v; }
    public BigDecimal getAnnualGrossIncome()             { return annualGrossIncome; }
    public void setAnnualGrossIncome(BigDecimal v)       { this.annualGrossIncome = v; }
    public Integer getHouseholdSize()                    { return householdSize; }
    public void setHouseholdSize(Integer v)              { this.householdSize = v; }
    public String getMaritalStatus()                     { return maritalStatus; }
    public void setMaritalStatus(String v)               { this.maritalStatus = v; }
    public String getCreditScoreBand()                   { return creditScoreBand; }
    public void setCreditScoreBand(String v)             { this.creditScoreBand = v; }
    public Boolean getDisabilityStatus()                 { return disabilityStatus; }
    public void setDisabilityStatus(Boolean v)           { this.disabilityStatus = v; }
    public String getSchoolAttended()                    { return schoolAttended; }
    public void setSchoolAttended(String v)              { this.schoolAttended = v; }
    public LocalDateTime getCreatedAt()                  { return createdAt; }
    public LocalDateTime getUpdatedAt()                  { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)            { this.updatedAt = v; }
}
