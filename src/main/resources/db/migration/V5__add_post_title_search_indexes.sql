CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text
LANGUAGE sql
IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT unaccent('unaccent', $1)
$$;

ALTER TABLE posts ADD COLUMN title_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', immutable_unaccent(title))) STORED;

CREATE INDEX idx_posts_title_fts ON posts USING GIN (title_tsv)
    WHERE status = 'APPROVED';

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_posts_title_trgm ON posts USING GIN (title gin_trgm_ops)
    WHERE status = 'APPROVED';

CREATE INDEX idx_posts_approved_created ON posts (created_at DESC)
    WHERE status = 'APPROVED';
