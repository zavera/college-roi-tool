CREATE TABLE IF NOT EXISTS fafsa_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES app_users(id),
    student_name            VARCHAR(255),
    date_of_birth           DATE,
    planning_year           INTEGER,
    extracted_data_json     TEXT,
    readiness_summary_json  TEXT,
    selected_options_json   TEXT,
    roadmap_json             TEXT,
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP
);
