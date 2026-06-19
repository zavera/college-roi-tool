-- Post-grad debt relief profiles: many per student; latest by created_at is the active view.
-- Default federal/private balances are summed from fafsa_aid_packages on page load.
-- Saved when "Analyze My Loans" is clicked.
CREATE TABLE IF NOT EXISTS post_grad_profiles (
    id                    BIGSERIAL     PRIMARY KEY,
    student_id            BIGINT        NOT NULL REFERENCES students(id),
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
