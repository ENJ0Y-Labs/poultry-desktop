-- Rebuild application_settings with the same canonical start-page values as the renderer.
-- Preserve each farm's existing start page, date format, and timestamps.
CREATE TABLE application_settings_new (
    farm_id TEXT PRIMARY KEY,
    start_page TEXT NOT NULL DEFAULT 'dashboard'
        CHECK (start_page IN (
            'dashboard', 'flocks', 'daily-work', 'feed-stock', 'health',
            'production', 'sales-money', 'reports', 'settings'
        )),
    date_format TEXT NOT NULL DEFAULT 'YYYY-MM-DD'
        CHECK (date_format IN ('YYYY-MM-DD', 'DD/MM/YYYY', 'DD-MM-YYYY')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE
);

INSERT INTO application_settings_new (farm_id, start_page, date_format, created_at, updated_at)
SELECT farm_id, start_page, date_format, created_at, updated_at
FROM application_settings;

DROP TABLE application_settings;
ALTER TABLE application_settings_new RENAME TO application_settings;
