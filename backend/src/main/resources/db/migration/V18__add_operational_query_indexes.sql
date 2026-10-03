CREATE INDEX IF NOT EXISTS idx_bird_population_batch_date_type
    ON bird_population_events(batch_id, event_date, event_type);

CREATE INDEX IF NOT EXISTS idx_egg_collections_batch_date
    ON egg_collections(batch_id, record_date);

CREATE INDEX IF NOT EXISTS idx_egg_sales_batch_date
    ON egg_sales(batch_id, record_date);

CREATE INDEX IF NOT EXISTS idx_sales_farm_date
    ON sales(farm_id, sale_date);

CREATE INDEX IF NOT EXISTS idx_sales_batch_date
    ON sales(batch_id, sale_date);

CREATE INDEX IF NOT EXISTS idx_expenses_farm_date
    ON expenses(farm_id, occurred_date);

CREATE INDEX IF NOT EXISTS idx_expenses_batch_date
    ON expenses(batch_id, occurred_date);

CREATE INDEX IF NOT EXISTS idx_daily_records_batch_date
    ON daily_records(batch_id, record_date);

CREATE INDEX IF NOT EXISTS idx_feed_usage_batch_date
    ON feed_usage(batch_id, usage_date);

CREATE INDEX IF NOT EXISTS idx_feed_usage_type_date
    ON feed_usage(feed_type_id, usage_date);

CREATE INDEX IF NOT EXISTS idx_feed_purchases_type_date
    ON feed_purchases(feed_type_id, purchase_date);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_item_date
    ON inventory_movements(inventory_item_id, movement_date);

CREATE INDEX IF NOT EXISTS idx_vaccination_records_batch_date
    ON vaccination_records(batch_id, record_date);

CREATE INDEX IF NOT EXISTS idx_weight_records_batch_date
    ON weight_records(batch_id, record_date);
