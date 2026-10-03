-- Existing migrations already cover the primary batch/date lookup paths.
-- These additional covering indexes target dashboard/report filters without
-- duplicating existing index names.

CREATE INDEX idx_bird_population_events_batch_type_date
    ON bird_population_events(batch_id, event_type, event_date);

CREATE INDEX idx_egg_collections_batch_good_cracked_date
    ON egg_collections(batch_id, good_eggs, cracked_eggs, record_date);

CREATE INDEX idx_inventory_movements_item_type_date
    ON inventory_movements(inventory_item_id, movement_type, movement_date);
