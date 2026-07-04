-- Subscription exemptions — 1:1 with users, manually granted (e.g. via Railway) to
-- give specific users free access without an active subscription.
CREATE TABLE exemptions (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    active     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exemptions_user UNIQUE (user_id)
);
CREATE INDEX idx_exemptions_user ON exemptions(user_id);
