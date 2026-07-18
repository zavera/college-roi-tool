package com.example.collegeroitool.model;

import jakarta.persistence.*;

/** Hardcoded FSA Handbook / studentaid.gov excerpts, keyed by topic + award year, used to build
 *  the FAFSA Prep asset-repositioning prompt without a live web search (token-cost tradeoff). */
@Entity
@Table(name = "fafsa_handbook_reference")
public class FafsaHandbookReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "award_year", nullable = false)
    private String awardYear;

    @Column(name = "chapter_label", nullable = false)
    private String chapterLabel;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_url")
    private String sourceUrl;

    public Long getId()                    { return id; }
    public String getTopic()               { return topic; }
    public void setTopic(String v)         { this.topic = v; }
    public String getAwardYear()           { return awardYear; }
    public void setAwardYear(String v)     { this.awardYear = v; }
    public String getChapterLabel()        { return chapterLabel; }
    public void setChapterLabel(String v)  { this.chapterLabel = v; }
    public String getContent()             { return content; }
    public void setContent(String v)       { this.content = v; }
    public String getSourceUrl()           { return sourceUrl; }
    public void setSourceUrl(String v)     { this.sourceUrl = v; }
}
