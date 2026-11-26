-- Extend the workflow without rewriting the shipped V35 lifecycle constraint.
-- lifecycle_status is authoritative; legacy status remains for upgrade compatibility.
ALTER TABLE report_submissions ADD COLUMN lifecycle_status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK (lifecycle_status IN (
        'DRAFT','SUBMITTED','PENDING_REVIEW','FLAGGED','BLOCKED',
        'CHANGES_REQUESTED','APPROVED','MERGING','MERGED','REJECTED'
    ));
UPDATE report_submissions SET lifecycle_status =
    CASE status WHEN 'PENDING_REVIEW' THEN 'PENDING_REVIEW' ELSE status END;

ALTER TABLE report_submissions ADD COLUMN idempotency_key TEXT;
ALTER TABLE report_submissions ADD COLUMN idempotency_payload_hash TEXT;
ALTER TABLE report_submissions ADD COLUMN scope_type TEXT NOT NULL DEFAULT 'FARM'
    CHECK (scope_type IN ('FARM','HOUSE','BATCH'));
ALTER TABLE report_submissions ADD COLUMN scope_id TEXT;
ALTER TABLE report_submissions ADD COLUMN event_date TEXT;
ALTER TABLE report_submissions ADD COLUMN late_reason TEXT;
ALTER TABLE report_submissions ADD COLUMN assigned_manager_user_id TEXT
    REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE report_submissions ADD COLUMN decision_version INTEGER;
ALTER TABLE report_submissions ADD COLUMN merged_at TEXT;
ALTER TABLE report_submissions ADD COLUMN merge_operation_key TEXT;

CREATE UNIQUE INDEX uq_report_submission_idempotency
    ON report_submissions(farm_id, author_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_report_submissions_lifecycle
    ON report_submissions(farm_id, lifecycle_status, updated_at DESC);
CREATE INDEX idx_report_submissions_assignee
    ON report_submissions(assigned_manager_user_id, lifecycle_status, updated_at DESC);

CREATE TABLE report_submission_check_runs (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    outcome TEXT NOT NULL CHECK (outcome IN ('PENDING_REVIEW','FLAGGED','BLOCKED')),
    checked_at TEXT NOT NULL,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT
);
CREATE INDEX idx_submission_check_runs_history
    ON report_submission_check_runs(submission_id, version_number, checked_at DESC);

CREATE TABLE report_submission_checks (
    id TEXT PRIMARY KEY,
    check_run_id TEXT NOT NULL REFERENCES report_submission_check_runs(id) ON DELETE RESTRICT,
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    check_code TEXT NOT NULL CHECK (length(trim(check_code)) BETWEEN 1 AND 80),
    outcome TEXT NOT NULL CHECK (outcome IN ('PASSED','WARNING','BLOCKED')),
    overrideable INTEGER NOT NULL DEFAULT 0 CHECK (overrideable IN (0,1)),
    explanation TEXT NOT NULL,
    details_json TEXT CHECK (details_json IS NULL OR json_valid(details_json)),
    checked_at TEXT NOT NULL,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_submission_merge_operation
    ON report_submissions(merge_operation_key)
    WHERE merge_operation_key IS NOT NULL;

CREATE INDEX idx_submission_checks_version
    ON report_submission_checks(submission_id, version_number, checked_at);
CREATE UNIQUE INDEX uq_submission_check_code_per_run
    ON report_submission_checks(check_run_id, check_code);

CREATE TABLE report_submission_comments (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    author_user_id TEXT NOT NULL,
    body TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 4000),
    created_at TEXT NOT NULL,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT,
    FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_submission_comments_history
    ON report_submission_comments(submission_id, version_number, created_at, id);

CREATE TABLE report_submission_decisions (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    manager_user_id TEXT NOT NULL,
    decision TEXT NOT NULL CHECK (decision IN ('CHANGES_REQUESTED','REJECTED','APPROVED')),
    reason TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT,
    FOREIGN KEY (manager_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (
        (decision = 'APPROVED' AND reason IS NULL)
        OR (decision IN ('CHANGES_REQUESTED','REJECTED') AND length(trim(reason)) BETWEEN 1 AND 2000)
    )
);
-- Multiple immutable decisions may exist for the same version if a later
-- revalidation flags the previously-approved version for a second review.

CREATE TABLE report_submission_assignments (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    manager_user_id TEXT NOT NULL,
    assigned_by_user_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (submission_id) REFERENCES report_submissions(id) ON DELETE RESTRICT,
    FOREIGN KEY (manager_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (assigned_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_submission_assignments_history
    ON report_submission_assignments(submission_id, created_at, id);

CREATE TABLE report_submission_links (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    official_record_type TEXT NOT NULL,
    official_record_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT,
    UNIQUE (submission_id, version_number, official_record_type, official_record_id)
);

CREATE TABLE report_submission_warning_overrides (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    check_id TEXT NOT NULL,
    manager_user_id TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (length(trim(reason)) BETWEEN 1 AND 1000),
    created_at TEXT NOT NULL,
    FOREIGN KEY (submission_id, version_number)
        REFERENCES report_submission_versions(submission_id, version_number) ON DELETE RESTRICT,
    FOREIGN KEY (check_id) REFERENCES report_submission_checks(id) ON DELETE RESTRICT,
    FOREIGN KEY (manager_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TRIGGER trg_report_submission_comments_no_update
BEFORE UPDATE ON report_submission_comments
BEGIN
    SELECT RAISE(ABORT, 'Report submission comments are immutable');
END;
CREATE TRIGGER trg_report_submission_comments_no_delete
BEFORE DELETE ON report_submission_comments
BEGIN
    SELECT RAISE(ABORT, 'Report submission comments are immutable');
END;
CREATE TRIGGER trg_report_submission_decisions_no_update
BEFORE UPDATE ON report_submission_decisions
BEGIN
    SELECT RAISE(ABORT, 'Report submission decisions are immutable');
END;
CREATE TRIGGER trg_report_submission_decisions_no_delete
BEFORE DELETE ON report_submission_decisions
BEGIN
    SELECT RAISE(ABORT, 'Report submission decisions are immutable');
END;
CREATE TRIGGER trg_report_submission_assignments_no_update
BEFORE UPDATE ON report_submission_assignments
BEGIN
    SELECT RAISE(ABORT, 'Report submission assignments are immutable');
END;
CREATE TRIGGER trg_report_submission_assignments_no_delete
BEFORE DELETE ON report_submission_assignments
BEGIN
    SELECT RAISE(ABORT, 'Report submission assignments are immutable');
END;
CREATE TRIGGER trg_report_submission_check_runs_no_update
BEFORE UPDATE ON report_submission_check_runs
BEGIN
    SELECT RAISE(ABORT, 'Report submission check runs are immutable');
END;
CREATE TRIGGER trg_report_submission_check_runs_no_delete
BEFORE DELETE ON report_submission_check_runs
BEGIN
    SELECT RAISE(ABORT, 'Report submission check runs are immutable');
END;
CREATE TRIGGER trg_report_submission_checks_no_update
BEFORE UPDATE ON report_submission_checks
BEGIN
    SELECT RAISE(ABORT, 'Report submission checks are immutable');
END;
CREATE TRIGGER trg_report_submission_checks_no_delete
BEFORE DELETE ON report_submission_checks
BEGIN
    SELECT RAISE(ABORT, 'Report submission checks are immutable');
END;
CREATE TRIGGER trg_report_submission_links_no_update
BEFORE UPDATE ON report_submission_links
BEGIN
    SELECT RAISE(ABORT, 'Report submission links are immutable');
END;
CREATE TRIGGER trg_report_submission_links_no_delete
BEFORE DELETE ON report_submission_links
BEGIN
    SELECT RAISE(ABORT, 'Report submission links are immutable');
END;

CREATE UNIQUE INDEX uq_submission_warning_override
    ON report_submission_warning_overrides(submission_id, version_number, check_id);

CREATE TRIGGER trg_report_submission_warning_overrides_no_update
BEFORE UPDATE ON report_submission_warning_overrides
BEGIN
    SELECT RAISE(ABORT, 'Report warning overrides are immutable');
END;
CREATE TRIGGER trg_report_submission_warning_overrides_no_delete
BEFORE DELETE ON report_submission_warning_overrides
BEGIN
    SELECT RAISE(ABORT, 'Report warning overrides are immutable');
END;
