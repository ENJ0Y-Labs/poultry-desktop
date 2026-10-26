ALTER TABLE farm_settings
    ADD COLUMN water_unit_label TEXT NOT NULL DEFAULT 'litres'
    CHECK (length(trim(water_unit_label)) BETWEEN 1 AND 20);
