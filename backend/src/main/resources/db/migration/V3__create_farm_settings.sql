CREATE TABLE farm_settings (
    farm_id TEXT PRIMARY KEY,
    default_crate_size INTEGER NOT NULL DEFAULT 30 CHECK (default_crate_size > 0),
    default_water_container_size INTEGER CHECK (
        default_water_container_size IS NULL OR default_water_container_size > 0
    ),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id)
);

CREATE TABLE water_container_sizes (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    capacity_units INTEGER NOT NULL CHECK (capacity_units > 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    UNIQUE (farm_id, capacity_units)
);

CREATE INDEX idx_water_container_sizes_farm_id
    ON water_container_sizes(farm_id);
