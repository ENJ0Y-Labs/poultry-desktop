ALTER TABLE batches ADD COLUMN population_count_status TEXT NOT NULL DEFAULT 'VERIFIED'
    CHECK (population_count_status IN ('ESTIMATED', 'VERIFIED'));

CREATE TABLE bird_population_adjustments (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    effective_date TEXT NOT NULL,
    previous_count INTEGER NOT NULL CHECK (previous_count >= 0),
    new_count INTEGER NOT NULL CHECK (new_count >= 0),
    difference INTEGER NOT NULL,
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    user_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (difference = new_count - previous_count)
);

CREATE INDEX idx_population_adjustments_batch_date
    ON bird_population_adjustments(batch_id, effective_date, created_at);
