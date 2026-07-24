CREATE TABLE startup (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    input_startup_payload  TEXT,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_startup_user ON startup(user_id);
