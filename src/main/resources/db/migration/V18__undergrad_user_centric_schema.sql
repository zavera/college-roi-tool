-- undergrad-calculator branch only.
-- Drops all student/institution/document tables that main branch uses,
-- then recreates the three profile tables linked to app_users.user_id directly.
-- main branch never receives this migration; its schema is unaffected.

-- -----------------------------------------------------------------------
-- 1. Drop tables in reverse-dependency order
-- -----------------------------------------------------------------------
DROP TABLE IF EXISTS student_institutions   CASCADE;
DROP TABLE IF EXISTS user_institutions      CASCADE;
DROP TABLE IF EXISTS document_kv_extracts   CASCADE;
DROP TABLE IF EXISTS student_documents      CASCADE;
DROP TABLE IF EXISTS scholarship_profiles   CASCADE;
DROP TABLE IF EXISTS fafsa_aid_packages     CASCADE;
DROP TABLE IF EXISTS post_grad_profiles     CASCADE;
DROP TABLE IF EXISTS students               CASCADE;
DROP TABLE IF EXISTS institutions           CASCADE;

-- -----------------------------------------------------------------------
-- 2. Scholarship profiles — many per user
--    One profile per search session; chatbot reads all rows for the user.
-- -----------------------------------------------------------------------
CREATE TABLE scholarship_profiles (
    id                  BIGSERIAL     PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    gpa                 VARCHAR(10),
    major               VARCHAR(255),
    state_of_residency  VARCHAR(100),
    citizenship_status  VARCHAR(100),
    ethnicity           VARCHAR(255),
    first_generation    BOOLEAN       NOT NULL DEFAULT FALSE,
    extracurriculars    TEXT,
    target_schools_json TEXT,
    additional_notes    TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scholarship_profiles_user ON scholarship_profiles(user_id);

-- -----------------------------------------------------------------------
-- 3. FAFSA aid packages — many per user, one per aid year
--    Saved when "Get AI Financial Summary" is clicked.
-- -----------------------------------------------------------------------
CREATE TABLE fafsa_aid_packages (
    id                    BIGSERIAL     PRIMARY KEY,
    user_id               BIGINT        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
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
    CONSTRAINT uq_fafsa_aid_user_year UNIQUE (user_id, aid_year)
);
CREATE INDEX idx_fafsa_aid_packages_user ON fafsa_aid_packages(user_id);

-- -----------------------------------------------------------------------
-- 4. Post-grad debt profiles — many per user, latest = active view
--    Saved when "Analyze My Loans" is clicked.
-- -----------------------------------------------------------------------
CREATE TABLE post_grad_profiles (
    id                    BIGSERIAL     PRIMARY KEY,
    user_id               BIGINT        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    federal_loan_balance  DECIMAL(12,2),
    private_loan_balance  DECIMAL(12,2),
    private_lender        VARCHAR(255),
    loan_servicer         VARCHAR(100),
    interest_rate         DECIMAL(5,2),
    grace_period_end_date DATE,
    employment_status     VARCHAR(50),
    employer_name         VARCHAR(255),
    annual_gross_income   DECIMAL(12,2),
    household_size        INT,
    marital_status        VARCHAR(50),
    credit_score_band     VARCHAR(20),
    disability_status     BOOLEAN,
    school_attended       VARCHAR(255),
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_post_grad_profiles_user ON post_grad_profiles(user_id);
