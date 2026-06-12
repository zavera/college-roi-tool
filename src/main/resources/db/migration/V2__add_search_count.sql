-- Add search_count column (tracks AI summary usage per account for the paywall).
-- IF NOT EXISTS is safe: if you already ran the manual ALTER TABLE fix this is a no-op.
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS search_count INTEGER NOT NULL DEFAULT 0;
