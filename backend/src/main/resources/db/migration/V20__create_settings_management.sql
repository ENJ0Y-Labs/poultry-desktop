CREATE TABLE expense_categories (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    UNIQUE (farm_id, name)
);
CREATE INDEX idx_expense_categories_farm_status ON expense_categories(farm_id, status);
INSERT INTO expense_categories (id, farm_id, name, status, created_at, updated_at)
SELECT lower(hex(randomblob(16))), id, 'FEED', 'ACTIVE', created_at, updated_at FROM farms;
INSERT INTO expense_categories (id, farm_id, name, status, created_at, updated_at)
SELECT lower(hex(randomblob(16))), id, 'DRUGS', 'ACTIVE', created_at, updated_at FROM farms;
INSERT INTO expense_categories (id, farm_id, name, status, created_at, updated_at)
SELECT lower(hex(randomblob(16))), id, 'OTHER', 'ACTIVE', created_at, updated_at FROM farms;
CREATE TABLE application_settings (
    farm_id TEXT PRIMARY KEY,
    start_page TEXT NOT NULL DEFAULT 'dashboard' CHECK (start_page IN ('dashboard', 'operations', 'reports', 'settings')),
    date_format TEXT NOT NULL DEFAULT 'YYYY-MM-DD' CHECK (date_format IN ('YYYY-MM-DD', 'DD/MM/YYYY', 'DD-MM-YYYY')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE
);
CREATE TABLE backup_settings (
    farm_id TEXT PRIMARY KEY,
    enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
    directory TEXT,
    interval_ms INTEGER NOT NULL DEFAULT 86400000 CHECK (interval_ms >= 3600000),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE
);
CREATE INDEX idx_backup_settings_enabled ON backup_settings(enabled);
INSERT INTO application_settings (farm_id, created_at, updated_at) SELECT id, created_at, updated_at FROM farms;
INSERT INTO backup_settings (farm_id, created_at, updated_at) SELECT id, created_at, updated_at FROM farms;
CREATE TABLE expenses_new (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    batch_id TEXT,
    category TEXT NOT NULL CHECK (length(trim(category)) > 0),
    amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
    occurred_date TEXT NOT NULL,
    reference_type TEXT,
    reference_id TEXT,
    description TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);
INSERT INTO expenses_new (id, farm_id, batch_id, category, amount_minor, occurred_date, reference_type, reference_id, description, created_at)
SELECT id, farm_id, batch_id, category, amount_minor, occurred_date, reference_type, reference_id, description, created_at FROM expenses;
DROP TABLE expenses;
ALTER TABLE expenses_new RENAME TO expenses;
CREATE INDEX idx_expenses_farm_date ON expenses(farm_id, occurred_date);
CREATE INDEX idx_expenses_reference ON expenses(reference_type, reference_id);
CREATE INDEX idx_expenses_farm_batch_date ON expenses(farm_id, batch_id, occurred_date);
