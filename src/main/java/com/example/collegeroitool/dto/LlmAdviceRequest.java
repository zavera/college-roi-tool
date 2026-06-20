package com.example.collegeroitool.dto;

public class LlmAdviceRequest {

    // ── Compare mode ──────────────────────────────────────────────────────────
    private Boolean compareMode;
    private java.util.List<java.util.Map<String, Object>> compareColleges;

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

    // Computed COA fields from frontend
    private Double computedCOA;
    private Double computedNetPrice;
    private Double computedUnmetNeed;
    private String residency;
    private String livingSituation;

    // Student profile fields
    private Double gpa;
    private String extracurriculars;
    private String academicAchievements;
    private String gender;
    private String race;
    private Boolean firstGeneration;

    // College-wide 6-yr median earnings (from Scorecard, distinct from major-specific earnings)
    private Double collegeWideEarnings;

    // Private loan amount for this aid year (stored in fafsa_aid_packages)
    private Double privateLoan;

    // Aid year for persistence — userId is resolved server-side, never from client
    private Integer aidYear;

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

    public Double getGpa() { return gpa; }
    public void setGpa(Double gpa) { this.gpa = gpa; }

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

    public Double getComputedCOA() { return computedCOA; }
    public void setComputedCOA(Double computedCOA) { this.computedCOA = computedCOA; }

    public Double getComputedNetPrice() { return computedNetPrice; }
    public void setComputedNetPrice(Double computedNetPrice) { this.computedNetPrice = computedNetPrice; }

    public Double getComputedUnmetNeed() { return computedUnmetNeed; }
    public void setComputedUnmetNeed(Double computedUnmetNeed) { this.computedUnmetNeed = computedUnmetNeed; }

    public String getResidency() { return residency; }
    public void setResidency(String residency) { this.residency = residency; }

    public String getLivingSituation() { return livingSituation; }
    public void setLivingSituation(String livingSituation) { this.livingSituation = livingSituation; }

    public Double getCollegeWideEarnings() { return collegeWideEarnings; }
    public void setCollegeWideEarnings(Double collegeWideEarnings) { this.collegeWideEarnings = collegeWideEarnings; }

    public Boolean getCompareMode() { return compareMode; }
    public void setCompareMode(Boolean compareMode) { this.compareMode = compareMode; }

    public java.util.List<java.util.Map<String, Object>> getCompareColleges() { return compareColleges; }
    public void setCompareColleges(java.util.List<java.util.Map<String, Object>> compareColleges) { this.compareColleges = compareColleges; }

    public Double getPrivateLoan()         { return privateLoan; }
    public void setPrivateLoan(Double v)   { this.privateLoan = v; }
    public Integer getAidYear()            { return aidYear; }
    public void setAidYear(Integer v)      { this.aidYear = v; }
}
