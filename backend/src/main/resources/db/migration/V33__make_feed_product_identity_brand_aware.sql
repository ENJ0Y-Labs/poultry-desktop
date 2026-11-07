-- Replace the legacy farm/name uniqueness rule with a brand-aware product identity.
-- Preserve feed IDs and every feed ledger relationship.
PRAGMA defer_foreign_keys = ON;

CREATE TABLE feed_types_new (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    unit TEXT NOT NULL CHECK (length(trim(unit)) > 0),
    applicable_type TEXT NOT NULL CHECK (applicable_type IN ('LAYER', 'BROILER', 'TURKEY', 'BOTH')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    kg_per_unit NUMERIC CHECK (kg_per_unit IS NULL OR kg_per_unit > 0),
    brand TEXT,
    packaging_name TEXT,
    package_weight_kg NUMERIC CHECK (package_weight_kg IS NULL OR package_weight_kg > 0),
    FOREIGN KEY (farm_id) REFERENCES farms(id)
);

INSERT INTO feed_types_new (
    id, farm_id, name, unit, applicable_type, status, created_at, updated_at,
    kg_per_unit, brand, packaging_name, package_weight_kg
)
SELECT
    id, farm_id, name, unit, applicable_type, status, created_at, updated_at,
    kg_per_unit, brand, packaging_name, package_weight_kg
FROM feed_types;

-- Copy dependent feed rows without foreign keys while replacing the parent table.
-- Recreate their real foreign keys only after feed_types has its final name.
CREATE TABLE feed_type_applicability_copy (
    feed_type_id TEXT NOT NULL,
    bird_type TEXT NOT NULL
);
INSERT INTO feed_type_applicability_copy (feed_type_id, bird_type)
SELECT feed_type_id, bird_type FROM feed_type_applicability;

CREATE TABLE feed_opening_balances_copy (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    feed_type_id TEXT NOT NULL,
    effective_date TEXT NOT NULL,
    quantity_milli INTEGER NOT NULL,
    unit TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_by TEXT,
    created_at TEXT NOT NULL
);
INSERT INTO feed_opening_balances_copy (
    id, farm_id, feed_type_id, effective_date, quantity_milli, unit, reason, created_by, created_at
)
SELECT
    id, farm_id, feed_type_id, effective_date, quantity_milli, unit, reason, created_by, created_at
FROM feed_opening_balances;

DROP TABLE feed_type_applicability;
DROP TABLE feed_opening_balances;
DROP TABLE feed_types;

ALTER TABLE feed_types_new RENAME TO feed_types;

CREATE TABLE feed_type_applicability (
    feed_type_id TEXT NOT NULL,
    bird_type TEXT NOT NULL CHECK (bird_type IN ('LAYER', 'BROILER', 'TURKEY')),
    PRIMARY KEY (feed_type_id, bird_type),
    FOREIGN KEY (feed_type_id) REFERENCES feed_types(id) ON DELETE CASCADE
);
INSERT INTO feed_type_applicability (feed_type_id, bird_type)
SELECT feed_type_id, bird_type FROM feed_type_applicability_copy;
DROP TABLE feed_type_applicability_copy;

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
INSERT INTO feed_opening_balances (
    id, farm_id, feed_type_id, effective_date, quantity_milli, unit, reason, created_by, created_at
)
SELECT
    id, farm_id, feed_type_id, effective_date, quantity_milli, unit, reason, created_by, created_at
FROM feed_opening_balances_copy;
DROP TABLE feed_opening_balances_copy;

CREATE INDEX idx_feed_types_farm_status ON feed_types(farm_id, status);
CREATE INDEX idx_feed_type_applicability_bird_type
    ON feed_type_applicability(bird_type, feed_type_id);
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

-- Null and blank brand represent the same unbranded product identity.
-- Keep the database key exact so legacy case-variant names are preserved.
-- FeedService trims and normalizes values before duplicate validation.
CREATE UNIQUE INDEX idx_feed_types_product_identity
    ON feed_types(farm_id, name, COALESCE(brand, ''));
