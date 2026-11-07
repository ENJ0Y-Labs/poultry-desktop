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

-- Preserve applicability rows as data while the referenced parent table is replaced.
-- Recreate the real foreign key only after feed_types has its final name.
CREATE TABLE feed_type_applicability_copy (
    feed_type_id TEXT NOT NULL,
    bird_type TEXT NOT NULL
);

INSERT INTO feed_type_applicability_copy (feed_type_id, bird_type)
SELECT feed_type_id, bird_type FROM feed_type_applicability;

DROP TABLE feed_type_applicability;
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

CREATE INDEX idx_feed_types_farm_status ON feed_types(farm_id, status);
CREATE INDEX idx_feed_type_applicability_bird_type
    ON feed_type_applicability(bird_type, feed_type_id);

-- Null and blank brand represent the same unbranded product identity.
-- Keep the database key exact so legacy case-variant names are preserved.
-- FeedService trims and normalizes values before duplicate validation.
CREATE UNIQUE INDEX idx_feed_types_product_identity
    ON feed_types(farm_id, name, COALESCE(brand, ''));
