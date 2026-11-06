-- Legacy houses receive an explicit, neutral migration default.
-- Do not infer type from a house code prefix. Administrators can classify legacy
-- temporary structures through House Management after the migration.
ALTER TABLE houses ADD COLUMN house_type TEXT NOT NULL DEFAULT 'PERMANENT'
    CHECK (house_type IN ('PERMANENT', 'TEMPORARY'));

CREATE INDEX idx_houses_farm_type_status ON houses(farm_id, house_type, status);
