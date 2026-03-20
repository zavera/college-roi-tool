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

    // Undergrad-specific FAFSA fields
    private Double pellGrant;
    private Double subsidizedLoan;
    private Double unsubsidizedLoan;
    private Double workStudy;
    private Double institutionalGrant;

    // Student profile fields
    private String extracurriculars;
    private String academicAchievements;
    private String gender;
    private String race;
    private Boolean firstGeneration;

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

    public Double getPellGrant() { return pellGrant; }
    public void setPellGrant(Double pellGrant) { this.pellGrant = pellGrant; }

    public Double getSubsidizedLoan() { return subsidizedLoan; }
    public void setSubsidizedLoan(Double subsidizedLoan) { this.subsidizedLoan = subsidizedLoan; }

    public Double getUnsubsidizedLoan() { return unsubsidizedLoan; }
    public void setUnsubsidizedLoan(Double unsubsidizedLoan) { this.unsubsidizedLoan = unsubsidizedLoan; }

    public Double getWorkStudy() { return workStudy; }
    public void setWorkStudy(Double workStudy) { this.workStudy = workStudy; }

    public Double getInstitutionalGrant() { return institutionalGrant; }
    public void setInstitutionalGrant(Double institutionalGrant) { this.institutionalGrant = institutionalGrant; }

    public String getExtracurriculars() { return extracurriculars; }
    public void setExtracurriculars(String extracurriculars) { this.extracurriculars = extracurriculars; }

    public String getAcademicAchievements() { return academicAchievements; }
    public void setAcademicAchievements(String academicAchievements) { this.academicAchievements = academicAchievements; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getRace() { return race; }
    public void setRace(String race) { this.race = race; }

    public Boolean getFirstGeneration() { return firstGeneration; }
    public void setFirstGeneration(Boolean firstGeneration) { this.firstGeneration = firstGeneration; }
}
