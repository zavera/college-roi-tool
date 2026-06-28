-- FAFSA Prep manual-entry table (undergrad-calculator branch).
-- One user can save many snapshots; each row is one planning session.
-- No document upload, no extraction — all fields entered by the user.

CREATE TABLE fafsa_prep_entries (
    id                          BIGSERIAL       PRIMARY KEY,
    user_id                     BIGINT          NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,

    -- ── Who added this entry ──────────────────────────────────────────────
    added_by                    VARCHAR(255),       -- display name or email of submitter

    -- ── Planning context ─────────────────────────────────────────────────
    label                       VARCHAR(255),       -- user-given name e.g. "2025-2026 plan"
    planning_year               INT,
    dependency_status           VARCHAR(20),        -- 'dependent' | 'independent'

    -- ── Student income ────────────────────────────────────────────────────
    student_agi                 DECIMAL(12,2),
    student_taxes_paid          DECIMAL(12,2),
    student_untaxed_income      DECIMAL(12,2),      -- SS benefits, child support rcvd, etc.
    student_work_study          DECIMAL(12,2),

    -- ── Student assets ────────────────────────────────────────────────────
    student_cash_savings        DECIMAL(12,2),
    student_investments         DECIMAL(12,2),      -- excl. retirement
    student_business_net_worth  DECIMAL(12,2),

    -- ── Household ────────────────────────────────────────────────────────
    household_size              INT,
    number_in_college           INT,

    -- ── Parent income ────────────────────────────────────────────────────
    parent_agi                  DECIMAL(12,2),
    parent_taxes_paid           DECIMAL(12,2),
    parent_untaxed_income       DECIMAL(12,2),
    parent_marital_status       VARCHAR(30),        -- 'married' | 'single' | 'divorced' | 'widowed'
    parent_age                  INT,                -- used in asset protection allowance

    -- ── Parent assets ────────────────────────────────────────────────────
    parent_cash_savings         DECIMAL(12,2),
    parent_investments          DECIMAL(12,2),
    parent_home_equity          DECIMAL(12,2),      -- primary residence (not counted on FAFSA, but useful)
    parent_retirement_savings   DECIMAL(12,2),      -- IRA/401k (not counted on FAFSA)
    parent_business_net_worth   DECIMAL(12,2),
    parent_529_balance          DECIMAL(12,2),

    -- ── Computed / AI output ─────────────────────────────────────────────
    estimated_sai               DECIMAL(12,2),
    asset_repositioning_json    TEXT,               -- Groq-generated strategies

    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fafsa_prep_entries_user ON fafsa_prep_entries(user_id);
