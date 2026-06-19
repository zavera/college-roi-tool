-- Institutions: each named entity (school, counseling center, etc.) that uses Astra
CREATE TABLE IF NOT EXISTS institutions (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    code       VARCHAR(100) NOT NULL UNIQUE,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- User ↔ Institution (many-to-many). One email can only join an institution once (enforced by PK).
CREATE TABLE IF NOT EXISTS user_institutions (
    user_id        BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    institution_id BIGINT NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, institution_id)
);

-- Student ↔ Institution (many-to-many). A student can be active/inactive at multiple institutions.
CREATE TABLE IF NOT EXISTS student_institutions (
    student_id     BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    institution_id BIGINT NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (student_id, institution_id)
);

-- Seed: Callisto Tech is the demo institution; all new users auto-enroll here.
INSERT INTO institutions (name, code) VALUES ('Callisto Tech', 'callisto-tech')
ON CONFLICT (code) DO NOTHING;
