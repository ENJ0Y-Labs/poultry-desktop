-- Phase 35: indexes verified against dashboard, batch dashboard,
-- reporting, search and filtering query patterns.

CREATE INDEX idx_batches_farm_status
    ON batches(farm_id, status);

CREATE INDEX idx_houses_farm_name
    ON houses(farm_id, name);

CREATE INDEX idx_egg_collections_batch_date_values
    ON egg_collections(batch_id, record_date, good_eggs, cracked_eggs);
