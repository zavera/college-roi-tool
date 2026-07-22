package com.example.collegeroitool.model;

import jakarta.persistence.*;

/** One active Y Combinator-backed company from the seeded catalog (see
 *  YcStartupCatalogSeeder). Deterministic ground truth for Startup Locator's personalized
 *  search — matched by industry/tag and location, then handed to Tavily for freshness
 *  enrichment (funding news, hiring confirmation) before Claude personalizes the writeup. */
@Entity
@Table(name = "yc_startup_catalog")
public class YcStartupCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "one_liner")
    private String oneLiner;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "website")
    private String website;

    @Column(name = "all_locations", length = 500)
    private String allLocations;

    // Comma-separated region tags, e.g. "United States of America,America / Canada,Remote"
    @Column(name = "regions", length = 500)
    private String regions;

    @Column(name = "industry")
    private String industry;

    @Column(name = "subindustry")
    private String subindustry;

    // Comma-separated tags, e.g. "Fintech,B2B,SaaS"
    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "team_size")
    private Integer teamSize;

    @Column(name = "batch")
    private String batch;

    @Column(name = "stage")
    private String stage;

    @Column(name = "is_hiring", nullable = false)
    private boolean hiring;

    @Column(name = "yc_url")
    private String ycUrl;

    public Long getId()                     { return id; }
    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public String getOneLiner()             { return oneLiner; }
    public void setOneLiner(String v)       { this.oneLiner = v; }
    public String getDescription()          { return description; }
    public void setDescription(String v)    { this.description = v; }
    public String getWebsite()              { return website; }
    public void setWebsite(String v)        { this.website = v; }
    public String getAllLocations()         { return allLocations; }
    public void setAllLocations(String v)   { this.allLocations = v; }
    public String getRegions()              { return regions; }
    public void setRegions(String v)        { this.regions = v; }
    public String getIndustry()             { return industry; }
    public void setIndustry(String v)       { this.industry = v; }
    public String getSubindustry()          { return subindustry; }
    public void setSubindustry(String v)    { this.subindustry = v; }
    public String getTags()                 { return tags; }
    public void setTags(String v)           { this.tags = v; }
    public Integer getTeamSize()            { return teamSize; }
    public void setTeamSize(Integer v)      { this.teamSize = v; }
    public String getBatch()                { return batch; }
    public void setBatch(String v)          { this.batch = v; }
    public String getStage()                { return stage; }
    public void setStage(String v)          { this.stage = v; }
    public boolean isHiring()               { return hiring; }
    public void setHiring(boolean v)        { this.hiring = v; }
    public String getYcUrl()                { return ycUrl; }
    public void setYcUrl(String v)          { this.ycUrl = v; }
}
