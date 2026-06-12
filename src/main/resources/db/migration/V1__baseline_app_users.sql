-- Baseline: create app_users if it doesn't exist yet (no-op on existing prod DB).
-- Flyway runs this once and marks it done; subsequent deploys skip it.
CREATE TABLE IF NOT EXISTS app_users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    name                VARCHAR(255),
    password_hash       VARCHAR(255),
    provider            VARCHAR(255) NOT NULL DEFAULT 'local',
    subscription_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP
);
