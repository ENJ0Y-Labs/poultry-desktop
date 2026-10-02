CREATE TABLE health_records (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    condition_problem TEXT NOT NULL CHECK (length(trim(condition_problem)) > 0),
    description TEXT NOT NULL CHECK (length(trim(description)) > 0),
    action TEXT NOT NULL CHECK (length(trim(action)) > 0),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_health_records_batch_date
    ON health_records(batch_id, record_date, created_at);

CREATE TABLE drug_records (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    drug TEXT NOT NULL CHECK (length(trim(drug)) > 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    cost_minor INTEGER NOT NULL CHECK (cost_minor > 0),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_drug_records_batch_date
    ON drug_records(batch_id, record_date, created_at);

CREATE TABLE vaccination_records (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    vaccine TEXT NOT NULL CHECK (length(trim(vaccine)) > 0),
    dose TEXT NOT NULL CHECK (length(trim(dose)) > 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    notes TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_vaccination_records_batch_date
    ON vaccination_records(batch_id, record_date, created_at);
