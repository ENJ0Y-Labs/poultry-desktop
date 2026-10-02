CREATE TABLE farms (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    location TEXT,
    timezone TEXT NOT NULL DEFAULT 'Africa/Lagos',
    currency TEXT NOT NULL DEFAULT 'NGN' CHECK (length(currency) = 3),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (length(trim(email)) > 0),
    password_hash TEXT NOT NULL CHECK (length(password_hash) > 0),
    full_name TEXT NOT NULL CHECK (length(trim(full_name)) > 0),
    role TEXT NOT NULL DEFAULT 'OWNER' CHECK (role = 'OWNER'),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE houses (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    code TEXT NOT NULL CHECK (length(trim(code)) > 0),
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    UNIQUE (farm_id, code),
    UNIQUE (farm_id, name)
);

CREATE TABLE suppliers (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    supplier_type TEXT NOT NULL DEFAULT 'SUPPLIER'
        CHECK (supplier_type IN ('SUPPLIER', 'SOURCE')),
    phone TEXT,
    email TEXT,
    address TEXT,
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE
);

CREATE TABLE batches (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    house_id TEXT NOT NULL,
    supplier_id TEXT,
    code TEXT NOT NULL UNIQUE CHECK (length(trim(code)) > 0),
    batch_type TEXT NOT NULL CHECK (batch_type IN ('LAYER', 'BROILER')),
    placement_date TEXT NOT NULL,
    initial_bird_count INTEGER NOT NULL CHECK (initial_bird_count > 0),
    original_purchase_cost_minor INTEGER CHECK (
        original_purchase_cost_minor IS NULL OR original_purchase_cost_minor >= 0
    ),
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SOLD', 'CLOSED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    FOREIGN KEY (house_id) REFERENCES houses(id),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE bird_purchases (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    supplier_id TEXT,
    purchase_date TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost_minor INTEGER NOT NULL CHECK (unit_cost_minor >= 0),
    total_cost_minor INTEGER NOT NULL CHECK (total_cost_minor >= 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE audit_logs (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    farm_id TEXT,
    batch_id TEXT,
    occurred_at TEXT NOT NULL,
    action TEXT NOT NULL CHECK (length(trim(action)) > 0),
    entity_type TEXT NOT NULL CHECK (length(trim(entity_type)) > 0),
    entity_id TEXT NOT NULL CHECK (length(trim(entity_id)) > 0),
    reason TEXT,
    changed_fields_json TEXT,
    before_json TEXT,
    after_json TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE TABLE batch_code_sequences (
    batch_type TEXT NOT NULL CHECK (batch_type IN ('LAYER', 'BROILER')),
    sequence_year INTEGER NOT NULL CHECK (sequence_year >= 2000),
    next_number INTEGER NOT NULL CHECK (next_number > 0),
    PRIMARY KEY (batch_type, sequence_year)
);

CREATE INDEX idx_houses_farm_id ON houses(farm_id);
CREATE INDEX idx_suppliers_farm_id ON suppliers(farm_id);
CREATE INDEX idx_batches_farm_id ON batches(farm_id);
CREATE INDEX idx_batches_house_id ON batches(house_id);
CREATE INDEX idx_batches_supplier_id ON batches(supplier_id);
CREATE INDEX idx_batches_type_status ON batches(batch_type, status);
CREATE INDEX idx_bird_purchases_batch_id ON bird_purchases(batch_id);
CREATE INDEX idx_bird_purchases_supplier_id ON bird_purchases(supplier_id);
CREATE INDEX idx_audit_logs_farm_id_occurred_at ON audit_logs(farm_id, occurred_at);
CREATE INDEX idx_audit_logs_batch_id_occurred_at ON audit_logs(batch_id, occurred_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
