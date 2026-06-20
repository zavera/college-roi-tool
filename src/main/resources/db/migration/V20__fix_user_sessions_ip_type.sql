-- Hibernate maps String to VARCHAR; INET is a PostgreSQL-specific type that
-- causes ddl-auto=validate to fail. Cast to TEXT to preserve existing values.
ALTER TABLE user_sessions ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;
