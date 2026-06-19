-- Students belong to one institution (inherited from the creating counselor at enrollment time).
ALTER TABLE students ADD COLUMN IF NOT EXISTS institution_id BIGINT REFERENCES institutions(id);
ALTER TABLE students ADD COLUMN IF NOT EXISTS active         BOOLEAN NOT NULL DEFAULT TRUE;
