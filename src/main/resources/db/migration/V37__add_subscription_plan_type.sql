ALTER TABLE subscriptions ADD COLUMN plan_type VARCHAR(16) NOT NULL DEFAULT 'MONTHLY';
ALTER TABLE subscriptions ADD COLUMN plan_start_date TIMESTAMP;
UPDATE subscriptions SET plan_start_date = created_at WHERE plan_start_date IS NULL;
