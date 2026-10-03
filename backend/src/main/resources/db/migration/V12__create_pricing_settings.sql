ALTER TABLE farm_settings ADD COLUMN target_margin_percent REAL;
ALTER TABLE farm_settings ADD COLUMN working_margin_percent REAL;

CREATE TABLE pricing_margin_history (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    changed_at TEXT NOT NULL,
    change_type TEXT NOT NULL CHECK (change_type IN ('TARGET_SET','TARGET_CONFIRMED','WORKING_UPDATED')),
    old_target_margin_percent REAL,
    new_target_margin_percent REAL,
    old_working_margin_percent REAL,
    new_working_margin_percent REAL,
    reason TEXT,
    reference_type TEXT,
    reference_id TEXT,
    FOREIGN KEY (farm_id) REFERENCES farms(id)
);

CREATE INDEX idx_pricing_margin_history_farm_date
    ON pricing_margin_history(farm_id, changed_at);
