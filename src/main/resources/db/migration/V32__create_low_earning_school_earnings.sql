-- FSA "Earnings Data Report" (Data Published March 2026), hardcoded/seeded per-school reference
-- data used by Award Assist to flag and display schools' FAFSA Submission Summary "Lower Earnings"
-- designation. Row content is loaded idempotently at startup by LowEarningSchoolEarningsSeeder
-- (not by this migration), so the same content lands in local H2 (Flyway disabled) and prod
-- Postgres alike. This dataset is static and goes stale — see the seeder for refresh instructions.
CREATE TABLE low_earning_school_earnings (
    id BIGSERIAL PRIMARY KEY,
    unit_id INTEGER NOT NULL,
    ope_id_8 VARCHAR(20),
    ope_id_6 VARCHAR(20),
    federal_school_code VARCHAR(20),
    institution_name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    state VARCHAR(2),
    earnings_reported NUMERIC(12,2),
    earnings_inflation_adjusted NUMERIC(12,2),
    hs_earnings_threshold_value NUMERIC(12,2),
    hs_earnings_threshold_type VARCHAR(20),
    lower_earnings_flag BOOLEAN,
    CONSTRAINT uq_low_earning_school_unit_id UNIQUE (unit_id)
);

CREATE INDEX idx_low_earning_school_normalized_name ON low_earning_school_earnings(normalized_name);
