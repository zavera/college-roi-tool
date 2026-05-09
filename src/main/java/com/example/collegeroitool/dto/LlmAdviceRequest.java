package com.example.collegeroitool.dto;

public class LlmAdviceRequest {

    private String collegeName;
    private Double federalLoan;
    private Double parentPlusLoan;
    private Double grantAmount;
    private Double scholarshipAmount;
    private Double netPrice;
    private Double sixYrEarnings;
    private String major;

    // Getters and Setters
    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public Double getFederalLoan() { return federalLoan; }
    public void setFederalLoan(Double federalLoan) { this.federalLoan = federalLoan; }

    public Double getParentPlusLoan() { return parentPlusLoan; }
    public void setParentPlusLoan(Double parentPlusLoan) { this.parentPlusLoan = parentPlusLoan; }

    public Double getGrantAmount() { return grantAmount; }
    public void setGrantAmount(Double grantAmount) { this.grantAmount = grantAmount; }

    public Double getScholarshipAmount() { return scholarshipAmount; }
    public void setScholarshipAmount(Double scholarshipAmount) { this.scholarshipAmount = scholarshipAmount; }

    public Double getNetPrice() { return netPrice; }
    public void setNetPrice(Double netPrice) { this.netPrice = netPrice; }

    public Double getSixYrEarnings() { return sixYrEarnings; }
    public void setSixYrEarnings(Double sixYrEarnings) { this.sixYrEarnings = sixYrEarnings; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
}
