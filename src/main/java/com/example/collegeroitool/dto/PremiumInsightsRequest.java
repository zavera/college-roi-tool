package com.example.collegeroitool.dto;

public class PremiumInsightsRequest {
    // Existing fields
    private String collegeName;
    private String major;
    private Double netPrice;
    private Double sixYrEarnings;

    // New routing field
    private String section;

    // New demographic/profile fields
    private String gender;
    private String race;
    private Boolean firstGen;
    private Double gpa;
    private String extracurriculars;
    private String academicAchievements;
    private Double loanAmount;
    private Double unmetNeed;

    // Getters and setters

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public Double getNetPrice() { return netPrice; }
    public void setNetPrice(Double netPrice) { this.netPrice = netPrice; }

    public Double getSixYrEarnings() { return sixYrEarnings; }
    public void setSixYrEarnings(Double sixYrEarnings) { this.sixYrEarnings = sixYrEarnings; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getRace() { return race; }
    public void setRace(String race) { this.race = race; }

    public Boolean getFirstGen() { return firstGen; }
    public void setFirstGen(Boolean firstGen) { this.firstGen = firstGen; }

    public Double getGpa() { return gpa; }
    public void setGpa(Double gpa) { this.gpa = gpa; }

    public String getExtracurriculars() { return extracurriculars; }
    public void setExtracurriculars(String extracurriculars) { this.extracurriculars = extracurriculars; }

    public String getAcademicAchievements() { return academicAchievements; }
    public void setAcademicAchievements(String academicAchievements) { this.academicAchievements = academicAchievements; }

    public Double getLoanAmount() { return loanAmount; }
    public void setLoanAmount(Double loanAmount) { this.loanAmount = loanAmount; }

    public Double getUnmetNeed() { return unmetNeed; }
    public void setUnmetNeed(Double unmetNeed) { this.unmetNeed = unmetNeed; }
}
