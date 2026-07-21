package com.example.collegeroitool.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Hardcoded FSA "Earnings Data Report" row (Data Published March 2026) for one institution,
 *  keyed by Scorecard/IPEDS Unit ID. Used by Award Assist to display the school's FAFSA
 *  Submission Summary "Lower Earnings" flag and underlying figures without a live web search
 *  (same token-cost tradeoff as {@link FafsaHandbookReference}). See
 *  LowEarningSchoolEarningsSeeder for the load path and refresh instructions. */
@Entity
@Table(name = "low_earning_school_earnings")
public class LowEarningSchoolEarnings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Integer unitId;

    @Column(name = "ope_id_8")
    private String opeId8;

    @Column(name = "ope_id_6")
    private String opeId6;

    @Column(name = "federal_school_code")
    private String federalSchoolCode;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    // Lowercased, punctuation-stripped institutionName, used for fast lookup/candidate matching.
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "state")
    private String state;

    @Column(name = "earnings_reported")
    private BigDecimal earningsReported;

    @Column(name = "earnings_inflation_adjusted")
    private BigDecimal earningsInflationAdjusted;

    @Column(name = "hs_earnings_threshold_value")
    private BigDecimal hsEarningsThresholdValue;

    @Column(name = "hs_earnings_threshold_type")
    private String hsEarningsThresholdType;

    @Column(name = "lower_earnings_flag")
    private Boolean lowerEarningsFlag;

    public Long getId()                                    { return id; }
    public Integer getUnitId()                             { return unitId; }
    public void setUnitId(Integer v)                       { this.unitId = v; }
    public String getOpeId8()                              { return opeId8; }
    public void setOpeId8(String v)                        { this.opeId8 = v; }
    public String getOpeId6()                              { return opeId6; }
    public void setOpeId6(String v)                        { this.opeId6 = v; }
    public String getFederalSchoolCode()                   { return federalSchoolCode; }
    public void setFederalSchoolCode(String v)             { this.federalSchoolCode = v; }
    public String getInstitutionName()                     { return institutionName; }
    public void setInstitutionName(String v)               { this.institutionName = v; }
    public String getNormalizedName()                      { return normalizedName; }
    public void setNormalizedName(String v)                { this.normalizedName = v; }
    public String getState()                               { return state; }
    public void setState(String v)                         { this.state = v; }
    public BigDecimal getEarningsReported()                { return earningsReported; }
    public void setEarningsReported(BigDecimal v)          { this.earningsReported = v; }
    public BigDecimal getEarningsInflationAdjusted()       { return earningsInflationAdjusted; }
    public void setEarningsInflationAdjusted(BigDecimal v) { this.earningsInflationAdjusted = v; }
    public BigDecimal getHsEarningsThresholdValue()        { return hsEarningsThresholdValue; }
    public void setHsEarningsThresholdValue(BigDecimal v)  { this.hsEarningsThresholdValue = v; }
    public String getHsEarningsThresholdType()             { return hsEarningsThresholdType; }
    public void setHsEarningsThresholdType(String v)       { this.hsEarningsThresholdType = v; }
    public Boolean getLowerEarningsFlag()                  { return lowerEarningsFlag; }
    public void setLowerEarningsFlag(Boolean v)            { this.lowerEarningsFlag = v; }
}
