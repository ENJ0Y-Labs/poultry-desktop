CREATE TABLE farm_incidents (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL REFERENCES farms(id) ON DELETE RESTRICT,
    reported_by TEXT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    incident_type TEXT NOT NULL CHECK (incident_type IN
        ('SUSPECTED_DISEASE','UNUSUAL_MORTALITY','WATER_FAILURE','FEED_FAILURE',
         'FIRE','FLOOD','SECURITY','EQUIPMENT_DAMAGE','OTHER')),
    severity TEXT NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 160),
    description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 4000),
    batch_id TEXT REFERENCES batches(id) ON DELETE RESTRICT,
    house_id TEXT REFERENCES houses(id) ON DELETE RESTRICT,
    event_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS','RESOLVED')),
    linked_submission_id TEXT REFERENCES report_submissions(id) ON DELETE RESTRICT,
    acknowledged_by TEXT REFERENCES users(id) ON DELETE RESTRICT,
    acknowledged_at TEXT,
    resolved_by TEXT REFERENCES users(id) ON DELETE RESTRICT,
    resolved_at TEXT,
    resolution_notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX idx_farm_incidents_queue ON farm_incidents(farm_id, status, created_at DESC);
CREATE INDEX idx_farm_incidents_author ON farm_incidents(reported_by, created_at DESC);

CREATE TABLE farm_incident_events (
    id TEXT PRIMARY KEY,
    incident_id TEXT NOT NULL REFERENCES farm_incidents(id) ON DELETE RESTRICT,
    actor_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL,
    details_json TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(details_json)),
    created_at TEXT NOT NULL
);
CREATE INDEX idx_farm_incident_events_history ON farm_incident_events(incident_id, created_at);
CREATE TRIGGER trg_farm_incident_events_no_update
BEFORE UPDATE ON farm_incident_events
BEGIN SELECT RAISE(ABORT, 'Incident events are immutable'); END;
CREATE TRIGGER trg_farm_incident_events_no_delete
BEFORE DELETE ON farm_incident_events
BEGIN SELECT RAISE(ABORT, 'Incident events are immutable'); END;

CREATE TABLE manager_notifications (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL REFERENCES farms(id) ON DELETE RESTRICT,
    recipient_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    notification_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL,
    read_at TEXT
);
CREATE UNIQUE INDEX uq_manager_notification_entity
    ON manager_notifications(recipient_user_id, notification_type, entity_id, title);
CREATE INDEX idx_manager_notifications_unread
    ON manager_notifications(recipient_user_id, read_at, created_at DESC);

CREATE TABLE reporting_schedules (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL REFERENCES farms(id) ON DELETE RESTRICT,
    report_type TEXT NOT NULL,
    scope_type TEXT NOT NULL CHECK (scope_type IN ('FARM','HOUSE','BATCH')),
    scope_id TEXT,
    batch_type TEXT,
    frequency TEXT NOT NULL CHECK (frequency IN ('DAILY','WEEKLY')),
    due_time TEXT NOT NULL,
    day_of_week INTEGER CHECK (day_of_week BETWEEN 1 AND 7),
    grace_minutes INTEGER NOT NULL DEFAULT 0 CHECK (grace_minutes BETWEEN 0 AND 1440),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
    created_by TEXT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK ((scope_type = 'FARM' AND scope_id IS NULL) OR (scope_type <> 'FARM' AND scope_id IS NOT NULL)),
    CHECK ((frequency = 'DAILY' AND day_of_week IS NULL) OR (frequency = 'WEEKLY' AND day_of_week IS NOT NULL))
);
CREATE INDEX idx_reporting_schedules_enabled ON reporting_schedules(farm_id, enabled, report_type);

CREATE TABLE reporting_thresholds (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL REFERENCES farms(id) ON DELETE RESTRICT,
    threshold_code TEXT NOT NULL CHECK (threshold_code IN ('UNUSUAL_MORTALITY_COUNT','UNUSUAL_MORTALITY_PERCENT')),
    scope_type TEXT NOT NULL CHECK (scope_type IN ('FARM','HOUSE','BATCH','BATCH_TYPE')),
    scope_id TEXT,
    batch_type TEXT,
    threshold_value REAL NOT NULL CHECK (threshold_value > 0),
    unit TEXT NOT NULL CHECK (unit IN ('BIRDS','PERCENT')),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
    created_by TEXT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX idx_reporting_thresholds_enabled ON reporting_thresholds(farm_id, enabled, threshold_code);

CREATE TABLE report_submission_attachments (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    mime_type TEXT NOT NULL CHECK (mime_type IN ('image/jpeg','image/png','application/pdf','text/plain')),
    file_size INTEGER NOT NULL CHECK (file_size BETWEEN 1 AND 5242880),
    sha256 TEXT NOT NULL,
    file_content BLOB NOT NULL,
    uploaded_by TEXT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    uploaded_at TEXT NOT NULL,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT
);
CREATE INDEX idx_report_submission_attachments_version
    ON report_submission_attachments(submission_id, version_number, uploaded_at);
CREATE TRIGGER trg_report_submission_attachments_no_update
BEFORE UPDATE ON report_submission_attachments
BEGIN SELECT RAISE(ABORT, 'Submission attachments are immutable'); END;
CREATE TRIGGER trg_report_submission_attachments_no_delete
BEFORE DELETE ON report_submission_attachments
BEGIN SELECT RAISE(ABORT, 'Submission attachments are immutable'); END;

CREATE TABLE incident_attachments (
    id TEXT PRIMARY KEY,
    incident_id TEXT NOT NULL REFERENCES farm_incidents(id) ON DELETE RESTRICT,
    file_name TEXT NOT NULL,
    mime_type TEXT NOT NULL CHECK (mime_type IN ('image/jpeg','image/png','application/pdf','text/plain')),
    file_size INTEGER NOT NULL CHECK (file_size BETWEEN 1 AND 5242880),
    sha256 TEXT NOT NULL,
    file_content BLOB NOT NULL,
    uploaded_by TEXT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    uploaded_at TEXT NOT NULL
);
CREATE INDEX idx_incident_attachments_incident ON incident_attachments(incident_id, uploaded_at);
CREATE TRIGGER trg_incident_attachments_no_update
BEFORE UPDATE ON incident_attachments
BEGIN SELECT RAISE(ABORT, 'Incident attachments are immutable'); END;
CREATE TRIGGER trg_incident_attachments_no_delete
BEFORE DELETE ON incident_attachments
BEGIN SELECT RAISE(ABORT, 'Incident attachments are immutable'); END;
