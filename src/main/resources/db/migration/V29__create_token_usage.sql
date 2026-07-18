-- Tracks token usage per Claude API call, used to enforce a $20/month per-user spend cap
-- (see TokenUsageService). Cost is computed at query time from token_in/token_out using each
-- model's current price, not stored, since Anthropic's per-token pricing can change.
CREATE TABLE token_usage (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    user_session_id VARCHAR(100),
    token_in        BIGINT       NOT NULL,
    token_out       BIGINT       NOT NULL,
    model_name      VARCHAR(100) NOT NULL,
    search_type_id  VARCHAR(20)  NOT NULL,
    date_created    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_token_usage_user_date ON token_usage (user_id, date_created);
