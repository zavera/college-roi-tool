package com.example.collegeroitool.dto;

public class DebtIntakeRequest {
    private Double federalLoanBalance;
    private Double privateLoanBalance;
    private String loanServicer;
    private Double interestRate;           // annual %, defaults to 6.53 if null
    private String gracePeriodEndDate;
    private String employmentStatus;       // employed-full, employed-part, unemployed, self-employed
    private String employerName;
    private Double annualGrossIncome;
    private Integer householdSize;
    private String maritalStatus;          // single, married-joint, married-separate
    private String creditScoreBand;        // below-580, 580-619, 620-659, 660-699, 700-739, 740+
    private Boolean disabilityStatus;
    private String schoolAttended;
    private String privateLender;           // name of private lender (required when privateLoanBalance > 0)
    private String hardshipType;           // economic, unemployment, medical, general
    private String hardshipDetails;

    // Getters and setters
    public Double getFederalLoanBalance() { return federalLoanBalance; }
    public void setFederalLoanBalance(Double v) { this.federalLoanBalance = v; }

    public Double getPrivateLoanBalance() { return privateLoanBalance; }
    public void setPrivateLoanBalance(Double v) { this.privateLoanBalance = v; }

    public String getLoanServicer() { return loanServicer; }
    public void setLoanServicer(String v) { this.loanServicer = v; }

    public Double getInterestRate() { return interestRate; }
    public void setInterestRate(Double v) { this.interestRate = v; }

    public String getGracePeriodEndDate() { return gracePeriodEndDate; }
    public void setGracePeriodEndDate(String v) { this.gracePeriodEndDate = v; }

    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String v) { this.employmentStatus = v; }

    public String getEmployerName() { return employerName; }
    public void setEmployerName(String v) { this.employerName = v; }

    public Double getAnnualGrossIncome() { return annualGrossIncome; }
    public void setAnnualGrossIncome(Double v) { this.annualGrossIncome = v; }

    public Integer getHouseholdSize() { return householdSize; }
    public void setHouseholdSize(Integer v) { this.householdSize = v; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String v) { this.maritalStatus = v; }

    public String getCreditScoreBand() { return creditScoreBand; }
    public void setCreditScoreBand(String v) { this.creditScoreBand = v; }

    public Boolean getDisabilityStatus() { return disabilityStatus; }
    public void setDisabilityStatus(Boolean v) { this.disabilityStatus = v; }

    public String getSchoolAttended() { return schoolAttended; }
    public void setSchoolAttended(String v) { this.schoolAttended = v; }

    public String getPrivateLender() { return privateLender; }
    public void setPrivateLender(String v) { this.privateLender = v; }

    public String getHardshipType() { return hardshipType; }
    public void setHardshipType(String v) { this.hardshipType = v; }

    public String getHardshipDetails() { return hardshipDetails; }
    public void setHardshipDetails(String v) { this.hardshipDetails = v; }
}
