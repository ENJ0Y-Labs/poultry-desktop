ALTER TABLE bird_population_events ADD COLUMN notes TEXT;

CREATE TABLE daily_records (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    UNIQUE (batch_id, record_date)
);

CREATE INDEX idx_daily_records_batch_date
    ON daily_records(batch_id, record_date);

CREATE TABLE water_usage (
    id TEXT PRIMARY KEY,
    daily_record_id TEXT NOT NULL,
    container_size_id TEXT NOT NULL,
    container_count INTEGER NOT NULL CHECK (container_count > 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (daily_record_id) REFERENCES daily_records(id) ON DELETE CASCADE,
    FOREIGN KEY (container_size_id) REFERENCES water_container_sizes(id)
);

CREATE INDEX idx_water_usage_daily_record
    ON water_usage(daily_record_id);
