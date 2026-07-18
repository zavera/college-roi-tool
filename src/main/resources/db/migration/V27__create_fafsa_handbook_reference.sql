-- Hardcoded FSA Handbook / studentaid.gov reference content for the FAFSA Prep
-- asset-repositioning prompt. Replaces a live web search for this tab to save tokens/cost.
-- Row content is seeded idempotently at startup by FafsaHandbookReferenceSeeder (not by this
-- migration), so the same content lands in local H2 (Flyway disabled) and prod Postgres alike.
CREATE TABLE fafsa_handbook_reference (
    id            BIGSERIAL PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    award_year    VARCHAR(20)  NOT NULL,
    chapter_label VARCHAR(255) NOT NULL,
    content       TEXT         NOT NULL,
    source_url    VARCHAR(500),
    CONSTRAINT uq_fafsa_handbook_reference_topic_year UNIQUE (topic, award_year)
);
