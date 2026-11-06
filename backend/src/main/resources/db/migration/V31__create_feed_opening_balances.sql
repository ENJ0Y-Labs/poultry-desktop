CREATE TABLE feed_opening_balances (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL REFERENCES farms(id),
    feed_type_id TEXT NOT NULL REFERENCES feed_types(id),
    effective_date TEXT NOT NULL,
    quantity_milli INTEGER NOT NULL CHECK (quantity_milli > 0),
    unit TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    created_by TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_feed_opening_balances_type_date
    ON feed_opening_balances(feed_type_id, effective_date, created_at);
CREATE TRIGGER feed_opening_balances_no_update
BEFORE UPDATE ON feed_opening_balances BEGIN
    SELECT RAISE(ABORT, 'Feed opening balances are append-only');
END;
CREATE TRIGGER feed_opening_balances_no_delete
BEFORE DELETE ON feed_opening_balances BEGIN
    SELECT RAISE(ABORT, 'Feed opening balances are append-only');
END;
