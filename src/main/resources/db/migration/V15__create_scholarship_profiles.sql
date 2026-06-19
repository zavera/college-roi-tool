-- One scholarship profile per student (upsert on student_id).
-- Saved when "Search Scholarships" is clicked; pre-loaded on student retrieval.
CREATE TABLE IF NOT EXISTS scholarship_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    student_id          BIGINT        NOT NULL UNIQUE REFERENCES students(id),
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
