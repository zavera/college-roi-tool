-- User-centric schema v2 (undergrad-calculator branch).
-- Replaces the ad-hoc app_users subscription/counter columns and the
-- computed-profile tables (fafsa_profiles, scholarship_profiles, post_grad_profiles,
-- fafsa_aid_packages, fafsa_prep_entries) with an explicit subscription/search-usage
-- model plus append-only per-tab input logs and a shared model_response log.

-- -----------------------------------------------------------------------
-- 1. Drop tables superseded by this schema
-- -----------------------------------------------------------------------
DROP TABLE IF EXISTS fafsa_profiles       CASCADE;
DROP TABLE IF EXISTS scholarship_profiles CASCADE;
DROP TABLE IF EXISTS post_grad_profiles   CASCADE;
DROP TABLE IF EXISTS fafsa_aid_packages   CASCADE;
DROP TABLE IF EXISTS fafsa_prep_entries   CASCADE;

-- -----------------------------------------------------------------------
-- 2. Rename app_users -> users; drop ad-hoc subscription/counter columns
--    (FK constraints from user_sessions/magic_link_tokens follow the rename)
-- -----------------------------------------------------------------------
ALTER TABLE app_users RENAME TO users;
ALTER TABLE users DROP COLUMN IF EXISTS subscription_active;
ALTER TABLE users DROP COLUMN IF EXISTS search_count;
ALTER TABLE users DROP COLUMN IF EXISTS debt_search_count;
ALTER TABLE users DROP COLUMN IF EXISTS fafsa_usage_count;
ALTER TABLE users DROP COLUMN IF EXISTS scholarship_search_count;
ALTER TABLE users DROP COLUMN IF EXISTS award_assist_search_count;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

-- -----------------------------------------------------------------------
-- 3. Global app config (key/value) — seeds the default subscription price
-- -----------------------------------------------------------------------
CREATE TABLE app_config (
    id           BIGSERIAL     PRIMARY KEY,
    config_key   VARCHAR(100)  NOT NULL,
    config_value VARCHAR(255)  NOT NULL,
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_config_key UNIQUE (config_key)
);
INSERT INTO app_config (config_key, config_value) VALUES ('subscription_amount_cents', '9900');

-- -----------------------------------------------------------------------
-- 4. Subscriptions — 1:1 with users, auto-created (inactive) on login
-- -----------------------------------------------------------------------
CREATE TABLE subscriptions (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    active       BOOLEAN       NOT NULL DEFAULT FALSE,
    amount_cents INT           NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscriptions_user UNIQUE (user_id)
);
CREATE INDEX idx_subscriptions_user ON subscriptions(user_id);

-- -----------------------------------------------------------------------
-- 5. Search usage — 1:1 with users, one counter per tab
-- -----------------------------------------------------------------------
CREATE TABLE search_usages (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fafsa       INT           NOT NULL DEFAULT 0,
    scholarship INT           NOT NULL DEFAULT 0,
    coa         INT           NOT NULL DEFAULT 0,
    postgrad    INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_search_usages_user UNIQUE (user_id)
);
CREATE INDEX idx_search_usages_user ON search_usages(user_id);

-- -----------------------------------------------------------------------
-- 6. Per-tab input logs — many rows per user, append-only
-- -----------------------------------------------------------------------
CREATE TABLE fafsa (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    input_fafsa_payload TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_fafsa_user ON fafsa(user_id);

CREATE TABLE scholarship (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    input_scholarship_payload TEXT,
    created_at                TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scholarship_user ON scholarship(user_id);

CREATE TABLE coa (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    input_coa_payload TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_coa_user ON coa(user_id);

CREATE TABLE postgrad (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    input_postgrad_payload TEXT,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_postgrad_user ON postgrad(user_id);

-- -----------------------------------------------------------------------
-- 7. Model response log — one row per LLM call, polymorphic input reference.
--    input_id is resolved in application code against the table named by
--    type_input_payload; no DB-level FK since the target table varies by type.
-- -----------------------------------------------------------------------
CREATE TABLE model_response (
    id                  BIGSERIAL     PRIMARY KEY,
    model_name          VARCHAR(255)  NOT NULL,
    type_input_payload  VARCHAR(50)   NOT NULL,
    output_payload      TEXT,
    input_id            BIGINT,
    response_status     INT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_model_response_input ON model_response(type_input_payload, input_id);

-- -----------------------------------------------------------------------
-- 8. Chatbot queries — many rows per user
-- -----------------------------------------------------------------------
CREATE TABLE chatbot (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query_input TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_chatbot_user ON chatbot(user_id);
