-- Product identity and packaging metadata are descriptive. Unknown weights stay NULL.
ALTER TABLE feed_types ADD COLUMN brand TEXT;
ALTER TABLE feed_types ADD COLUMN packaging_name TEXT;
ALTER TABLE feed_types ADD COLUMN package_weight_kg NUMERIC
    CHECK (package_weight_kg IS NULL OR package_weight_kg > 0);
