-- Full-text search GIN index on name + description.
-- We coalesce description because it can be null.
CREATE INDEX IF NOT EXISTS idx_products_fts
ON products
USING GIN (to_tsvector('english', name || ' ' || coalesce(description, '')));

-- Optional: category + created_at is already indexed via JPA, but GIN is separate.