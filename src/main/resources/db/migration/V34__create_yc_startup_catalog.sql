-- Seed catalog of active Y Combinator-backed companies (name, location, industry/tags, hiring
-- status), sourced from the public yc-oss community mirror of YC's company directory
-- (https://yc-oss.github.io/api/companies/all.json — no signup/API key required). This is the
-- deterministic "real companies" backbone for the Startup Locator's personalized search: Tavily
-- is layered on top per-candidate for freshness (recent funding news, active hiring confirmation)
-- rather than trying to build a full startup database from scratch. Loaded idempotently at
-- startup by YcStartupCatalogSeeder (not by this migration) — see that class for refresh notes.
CREATE TABLE yc_startup_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    one_liner VARCHAR(500),
    description TEXT,
    website VARCHAR(500),
    all_locations VARCHAR(500),
    regions VARCHAR(500),
    industry VARCHAR(255),
    subindustry VARCHAR(255),
    tags VARCHAR(500),
    team_size INTEGER,
    batch VARCHAR(50),
    stage VARCHAR(50),
    is_hiring BOOLEAN NOT NULL DEFAULT FALSE,
    yc_url VARCHAR(500)
);

CREATE INDEX idx_yc_startup_catalog_industry ON yc_startup_catalog(industry);
CREATE INDEX idx_yc_startup_catalog_is_hiring ON yc_startup_catalog(is_hiring);
