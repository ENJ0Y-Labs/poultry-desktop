CREATE TABLE inventory_items (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    category TEXT NOT NULL CHECK (category IN ('DRUG', 'VACCINE', 'SUPPLY')),
    unit TEXT NOT NULL CHECK (length(trim(unit)) > 0),
    reorder_level NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id)
);

CREATE UNIQUE INDEX idx_inventory_items_farm_name
    ON inventory_items(farm_id, name COLLATE NOCASE);

CREATE INDEX idx_inventory_items_farm_category
    ON inventory_items(farm_id, category, status);

CREATE TABLE inventory_movements (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    inventory_item_id TEXT NOT NULL,
    movement_date TEXT NOT NULL,
    movement_type TEXT NOT NULL CHECK (
        movement_type IN ('RECEIVE', 'ISSUE', 'ADJUST_IN', 'ADJUST_OUT', 'WASTE')
    ),
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    source TEXT NOT NULL CHECK (length(trim(source)) > 0),
    batch_id TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_inventory_movements_item_date
    ON inventory_movements(inventory_item_id, movement_date, created_at);

CREATE INDEX idx_inventory_movements_farm_date
    ON inventory_movements(farm_id, movement_date);

CREATE INDEX idx_inventory_movements_batch_date
    ON inventory_movements(batch_id, movement_date);

ALTER TABLE feed_usage ADD COLUMN reason TEXT;

CREATE INDEX idx_feed_usage_reason ON feed_usage(feed_type_id, usage_date);
