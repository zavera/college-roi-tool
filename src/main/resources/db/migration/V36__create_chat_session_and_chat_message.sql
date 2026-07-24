CREATE TABLE chat_session (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_chat_session_user ON chat_session(user_id);

CREATE TABLE chat_message (
    id              BIGSERIAL PRIMARY KEY,
    chat_session_id BIGINT    NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    user_id         BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_chat_message_session ON chat_message(chat_session_id);
CREATE INDEX idx_chat_message_user ON chat_message(user_id);
