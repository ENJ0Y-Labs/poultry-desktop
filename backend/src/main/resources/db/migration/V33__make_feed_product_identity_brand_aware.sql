-- Replace the legacy name-only uniqueness rule with brand-aware product identity.
-- Preserve feed IDs so purchases, usage, opening stock and applicability remain attached.
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

DROP TABLE feed_types;
ALTER TABLE feed_types_new RENAME TO feed_types;

CREATE INDEX idx_feed_types_farm_status ON feed_types(farm_id, status);

-- Null and blank brand represent the same unbranded product identity.
-- Keep the database key exact so this migration can preserve legacy rows that
-- differ only by casing. FeedService trims and normalizes before duplicate checks.
CREATE UNIQUE INDEX idx_feed_types_product_identity
    ON feed_types(farm_id, name, COALESCE(brand, ''));
