-- Students: identity = (user_id, first_name, last_name, date_of_birth)
-- Duplicate additions for the same user are blocked by the unique constraint.
CREATE TABLE IF NOT EXISTS students (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES app_users(id),
    first_name      VARCHAR(255) NOT NULL,
    middle_name     VARCHAR(255),
    last_name       VARCHAR(255) NOT NULL,
    date_of_birth   DATE NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_student_per_user UNIQUE (user_id, first_name, last_name, date_of_birth)
);

-- Documents: one student has many documents; each document has a blob URL.
-- active=false soft-deletes a document without removing its KV history.
CREATE TABLE IF NOT EXISTS student_documents (
    id                  BIGSERIAL PRIMARY KEY,
    student_id          BIGINT NOT NULL REFERENCES students(id),
    blob_name           TEXT NOT NULL,
    blob_url            TEXT NOT NULL,
    original_filename   VARCHAR(512),
    uploaded_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    active              BOOLEAN NOT NULL DEFAULT TRUE
);

-- KV extracts: exactly one record per document (enforced by UNIQUE on document_id).
-- kv_json = '{}' when extraction produced no pairs (re-extraction will be attempted on next load).
CREATE TABLE IF NOT EXISTS document_kv_extracts (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL UNIQUE REFERENCES student_documents(id),
    kv_json         TEXT NOT NULL DEFAULT '{}',
    extracted_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
