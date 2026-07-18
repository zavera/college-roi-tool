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

    // No @Lob here: the column is plain Postgres TEXT (see V27 migration). Hibernate 6's @Lob on a
    // String maps to Types.CLOB, which the Postgres JDBC driver reads via its Large Object API —
    // that API requires a non-autocommit connection and threw "Large Objects may not be used in
    // auto-commit mode" when this seeder ran at startup outside a transactional web request.
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
