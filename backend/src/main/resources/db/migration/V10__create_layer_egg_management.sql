CREATE TABLE egg_collections (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    good_eggs INTEGER NOT NULL CHECK (good_eggs >= 0),
    cracked_eggs INTEGER NOT NULL CHECK (cracked_eggs >= 0),
    notes TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    CHECK (good_eggs + cracked_eggs > 0)
);

CREATE INDEX idx_egg_collections_batch_date
    ON egg_collections(batch_id, record_date, created_at);

CREATE TABLE egg_sales (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    customer TEXT NOT NULL CHECK (length(trim(customer)) > 0),
    sold_eggs INTEGER NOT NULL CHECK (sold_eggs > 0),
    crate_size INTEGER NOT NULL CHECK (crate_size > 0),
    price_per_crate_minor INTEGER NOT NULL CHECK (price_per_crate_minor > 0),
    total_amount_minor INTEGER NOT NULL CHECK (total_amount_minor > 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_egg_sales_batch_date
    ON egg_sales(batch_id, record_date, created_at);
