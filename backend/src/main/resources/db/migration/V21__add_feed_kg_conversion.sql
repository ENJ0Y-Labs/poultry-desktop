ALTER TABLE feed_types
    ADD COLUMN kg_per_unit NUMERIC
    CHECK (kg_per_unit IS NULL OR kg_per_unit > 0);

-- Kilograms already have a canonical conversion. Never guess conversions for
-- existing bags or other units; operators must configure those explicitly.
UPDATE feed_types
SET kg_per_unit = 1
WHERE lower(trim(unit)) = 'kg';
