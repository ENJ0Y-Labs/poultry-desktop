-- Keep the legacy users.role column intact for upgrade compatibility.
-- The authoritative operational role lives in user_roles and is joined by every auth lookup.
CREATE TABLE user_roles (
    user_id TEXT PRIMARY KEY,
    role TEXT NOT NULL CHECK (role IN ('WORKER', 'ADMINISTRATOR', 'MANAGER')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    updated_by TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- Existing OWNER accounts retain administrative control after upgrade.
INSERT INTO user_roles (user_id, role, created_at, updated_at, updated_by)
SELECT id, 'ADMINISTRATOR', created_at, updated_at, NULL
FROM users;

CREATE INDEX idx_user_roles_role ON user_roles(role);

CREATE TABLE report_submissions (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    author_user_id TEXT NOT NULL,
    report_type TEXT NOT NULL CHECK (length(trim(report_type)) BETWEEN 1 AND 80),
    title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 160),
    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED','APPROVED','REJECTED','MERGED')),
    current_version INTEGER NOT NULL DEFAULT 1 CHECK (current_version > 0),
    submitted_version INTEGER,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    submitted_at TEXT,
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE RESTRICT,
    FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (submitted_version IS NULL OR submitted_version > 0)
);

CREATE INDEX idx_report_submissions_author ON report_submissions(author_user_id, updated_at DESC);
CREATE INDEX idx_report_submissions_farm_status ON report_submissions(farm_id, status, updated_at DESC);

CREATE TABLE report_submission_versions (
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    payload_json TEXT NOT NULL CHECK (json_valid(payload_json)),
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    submitted_at TEXT,
    PRIMARY KEY (submission_id, version_number),
    FOREIGN KEY (submission_id) REFERENCES report_submissions(id) ON DELETE RESTRICT,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_report_submission_versions_created_by
    ON report_submission_versions(created_by, created_at DESC);

CREATE TABLE report_submission_events (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    version_number INTEGER,
    actor_user_id TEXT NOT NULL,
    event_type TEXT NOT NULL CHECK (length(trim(event_type)) BETWEEN 1 AND 60),
    details_json TEXT CHECK (details_json IS NULL OR json_valid(details_json)),
    occurred_at TEXT NOT NULL,
    FOREIGN KEY (submission_id) REFERENCES report_submissions(id) ON DELETE RESTRICT,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT,
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_report_submission_events_history
    ON report_submission_events(submission_id, occurred_at, id);

-- Every saved version and lifecycle event is append-only, including drafts.
CREATE TRIGGER trg_report_submission_versions_no_update
BEFORE UPDATE ON report_submission_versions
BEGIN
    SELECT RAISE(ABORT, 'Report submission versions are immutable');
END;

CREATE TRIGGER trg_report_submission_versions_no_delete
BEFORE DELETE ON report_submission_versions
BEGIN
    SELECT RAISE(ABORT, 'Report submission versions are immutable');
END;

CREATE TRIGGER trg_report_submission_events_no_update
BEFORE UPDATE ON report_submission_events
BEGIN
    SELECT RAISE(ABORT, 'Report submission events are immutable');
END;

CREATE TRIGGER trg_report_submission_events_no_delete
BEFORE DELETE ON report_submission_events
BEGIN
    SELECT RAISE(ABORT, 'Report submission events are immutable');
END;
