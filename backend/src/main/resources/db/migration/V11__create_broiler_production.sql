CREATE TABLE weight_records (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    sample_quantity INTEGER NOT NULL CHECK (sample_quantity > 0),
    total_weight_grams INTEGER NOT NULL CHECK (total_weight_grams > 0),
    notes TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_weight_records_batch_date
    ON weight_records(batch_id, record_date, created_at);

CREATE TABLE bird_sales (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price_per_bird_minor INTEGER NOT NULL CHECK (price_per_bird_minor > 0),
    total_amount_minor INTEGER NOT NULL CHECK (total_amount_minor > 0),
    customer TEXT NOT NULL CHECK (length(trim(customer)) > 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_bird_sales_batch_date
    ON bird_sales(batch_id, record_date, created_at);

CREATE INDEX idx_bird_sales_customer
    ON bird_sales(customer);
