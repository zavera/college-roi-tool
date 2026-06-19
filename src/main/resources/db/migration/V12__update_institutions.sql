ALTER TABLE institutions ADD COLUMN IF NOT EXISTS short_name VARCHAR(100);
ALTER TABLE institutions ADD COLUMN IF NOT EXISTS full_name  VARCHAR(255);

-- Backfill from existing data: code → short_name, name → full_name
UPDATE institutions SET full_name  = name WHERE full_name  IS NULL;
UPDATE institutions SET short_name = code WHERE short_name IS NULL;
