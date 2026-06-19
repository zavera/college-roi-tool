-- Institution-specific email per user — one email may only appear once per institution.
ALTER TABLE user_institutions ADD COLUMN IF NOT EXISTS email VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_institution_email
    ON user_institutions (institution_id, email)
    WHERE email IS NOT NULL;
