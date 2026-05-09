package com.example.collegeroitool.dto;

public class PremiumInsightsRequest {
    private String collegeName;
    private String major;
    private Double netPrice;
    private Double sixYrEarnings;

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public Double getNetPrice() { return netPrice; }
    public void setNetPrice(Double netPrice) { this.netPrice = netPrice; }

    public Double getSixYrEarnings() { return sixYrEarnings; }
    public void setSixYrEarnings(Double sixYrEarnings) { this.sixYrEarnings = sixYrEarnings; }
}
