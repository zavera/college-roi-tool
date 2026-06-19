-- FAFSA aid package data: one row per (student, aid_year).
-- Separate from fafsa_profiles (FAFSA Prep tab / document extraction).
-- Saved when "Get AI Financial Summary" is clicked; pre-loaded on student retrieval (latest by created_at).
-- Also provides the default federal/private loan balances for post_grad_profiles.
CREATE TABLE IF NOT EXISTS fafsa_aid_packages (
    id                    BIGSERIAL     PRIMARY KEY,
    student_id            BIGINT        NOT NULL REFERENCES students(id),
    aid_year              INT           NOT NULL,
    college_name          VARCHAR(255),
    major                 VARCHAR(255),
    residency             VARCHAR(50),
    living_situation      VARCHAR(50),
    cost_of_attendance    DECIMAL(12,2),
    net_price             DECIMAL(12,2),
    unmet_need            DECIMAL(12,2),
    pell_grant            DECIMAL(12,2),
    institutional_grant   DECIMAL(12,2),
    subsidized_loan       DECIMAL(12,2),
    unsubsidized_loan     DECIMAL(12,2),
    parent_plus_loan      DECIMAL(12,2),
    private_loan          DECIMAL(12,2),
    scholarship_amount    DECIMAL(12,2),
    work_study            DECIMAL(12,2),
    six_yr_earnings       DECIMAL(12,2),
    college_wide_earnings DECIMAL(12,2),
    efc_sai               DECIMAL(12,2),
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fafsa_aid_student_year UNIQUE (student_id, aid_year)
);
