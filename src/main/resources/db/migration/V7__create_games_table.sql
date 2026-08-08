CREATE TABLE games (
    id UUID PRIMARY KEY,
    white_id UUID NOT NULL REFERENCES users(id),
    black_id UUID NOT NULL REFERENCES users(id),
    pgn TEXT,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    time_minutes INT NOT NULL DEFAULT 10,
    increment_seconds INT NOT NULL DEFAULT 0,
    variant VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    rated BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    result VARCHAR(20),
    result_reason VARCHAR(30),
    source VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_games_white_id ON games (white_id);
CREATE INDEX idx_games_black_id ON games (black_id);

CREATE TRIGGER trg_games_updated_at
BEFORE UPDATE ON games FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
