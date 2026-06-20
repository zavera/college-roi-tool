-- Single-session-per-user enforcement.
-- On login: upsert by user_id, replacing the old token. The old session_token
-- becomes invalid, kicking out any other logged-in device for that user.
-- AuthController must invalidate the Spring session whose token no longer matches.

CREATE TABLE user_sessions (
    user_id         BIGINT        NOT NULL PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    session_token   VARCHAR(255)  NOT NULL,
    ip_address      INET,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    last_active_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_user_sessions_token ON user_sessions(session_token);
