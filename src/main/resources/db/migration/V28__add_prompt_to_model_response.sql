-- Tracks which prompt template file produced each model_response row, so we can trace
-- a stored response back to the exact prompt version that generated it.
ALTER TABLE model_response ADD COLUMN prompt VARCHAR(255);
