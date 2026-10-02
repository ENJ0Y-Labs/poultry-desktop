CREATE TABLE feed_types (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    unit TEXT NOT NULL CHECK (length(trim(unit)) > 0),
    applicable_type TEXT NOT NULL CHECK (applicable_type IN ('LAYER', 'BROILER', 'BOTH')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    UNIQUE (farm_id, name)
);

CREATE INDEX idx_feed_types_farm_status ON feed_types(farm_id, status);

CREATE TABLE feed_purchases (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    feed_type_id TEXT NOT NULL,
    supplier_id TEXT,
    purchase_date TEXT NOT NULL,
    quantity_milli INTEGER NOT NULL CHECK (quantity_milli > 0),
    unit TEXT NOT NULL CHECK (length(trim(unit)) > 0),
    total_cost_minor INTEGER NOT NULL CHECK (total_cost_minor > 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    FOREIGN KEY (feed_type_id) REFERENCES feed_types(id),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE INDEX idx_feed_purchases_type_date
    ON feed_purchases(feed_type_id, purchase_date, created_at);

CREATE TABLE feed_usage (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    feed_type_id TEXT NOT NULL,
    usage_date TEXT NOT NULL,
    quantity_milli INTEGER NOT NULL CHECK (quantity_milli > 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (feed_type_id) REFERENCES feed_types(id)
);

CREATE INDEX idx_feed_usage_type_date
    ON feed_usage(feed_type_id, usage_date, created_at);

CREATE INDEX idx_feed_usage_batch_date
    ON feed_usage(batch_id, usage_date, created_at);

CREATE TABLE expenses (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('FEED', 'DRUGS', 'OTHER')),
    amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
    occurred_date TEXT NOT NULL,
    reference_type TEXT,
    reference_id TEXT,
    description TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id)
);

CREATE INDEX idx_expenses_farm_date ON expenses(farm_id, occurred_date);
CREATE INDEX idx_expenses_reference ON expenses(reference_type, reference_id);
