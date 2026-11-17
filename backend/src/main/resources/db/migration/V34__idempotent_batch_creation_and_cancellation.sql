-- Add a durable idempotency ledger and a non-destructive cancelled lifecycle state.
PRAGMA defer_foreign_keys = ON;

CREATE TABLE batches_new (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    house_id TEXT NOT NULL,
    supplier_id TEXT,
    code TEXT NOT NULL UNIQUE CHECK(length(trim(code)) > 0),
    batch_type TEXT NOT NULL CHECK(batch_type IN ('LAYER','BROILER','TURKEY')),
    placement_date TEXT NOT NULL,
    initial_bird_count INTEGER NOT NULL CHECK(initial_bird_count > 0),
    original_purchase_cost_minor INTEGER CHECK(
        original_purchase_cost_minor IS NULL OR original_purchase_cost_minor >= 0
    ),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','SOLD','CLOSED','CANCELLED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    population_count_status TEXT NOT NULL DEFAULT 'ESTIMATED'
        CHECK(population_count_status IN ('ESTIMATED','VERIFIED')),
    FOREIGN KEY(farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    FOREIGN KEY(house_id) REFERENCES houses(id),
    FOREIGN KEY(supplier_id) REFERENCES suppliers(id)
);

INSERT INTO batches_new (
    id, farm_id, house_id, supplier_id, code, batch_type, placement_date,
    initial_bird_count, original_purchase_cost_minor, status, created_at,
    updated_at, population_count_status
)
SELECT
    id, farm_id, house_id, supplier_id, code, batch_type, placement_date,
    initial_bird_count, original_purchase_cost_minor, status, created_at,
    updated_at, population_count_status
FROM batches;

DROP TABLE batches;
ALTER TABLE batches_new RENAME TO batches;

CREATE INDEX idx_batches_farm_id ON batches(farm_id);
CREATE INDEX idx_batches_house_id ON batches(house_id);
CREATE INDEX idx_batches_supplier_id ON batches(supplier_id);
CREATE INDEX idx_batches_type_status ON batches(batch_type, status);

CREATE TABLE batch_create_requests (
    farm_id TEXT NOT NULL,
    idempotency_key TEXT NOT NULL CHECK(length(trim(idempotency_key)) BETWEEN 1 AND 128),
    request_hash TEXT NOT NULL CHECK(length(request_hash) = 64),
    batch_id TEXT,
    created_at TEXT NOT NULL,
    PRIMARY KEY (farm_id, idempotency_key),
    FOREIGN KEY(farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    FOREIGN KEY(batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_batch_create_requests_batch_id ON batch_create_requests(batch_id);
