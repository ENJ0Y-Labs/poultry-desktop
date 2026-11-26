# Decision: role-based report submission and review workflow

## Status
Proposed implementation architecture for Issue #287. No database schema is changed by this decision.

## Existing constraints
- The application currently has one OWNER role. The users table constrains role to OWNER, and authentication returns that role.
- The existing user status is ACTIVE/SUSPENDED. Suspended users are already excluded from session lookup.
- Audit history is append-only and written through AuditRepository.
- Farm records are owned by existing domain services and repositories. Posted transactions use reversal-plus-replacement rather than in-place edits.
- Spring Boot is the only API and persistence boundary. The renderer never accesses SQLite.
- The application must remain local-first and usable offline.

## Proposed identity and authorization model
Introduce one authoritative operational role per user: WORKER, ADMINISTRATOR, or MANAGER. Existing OWNER accounts migrate to ADMINISTRATOR because the current owner account is the application owner. The legacy users.role field should be retired from authorization decisions; a forward-only migration will rebuild/replace its constraint safely or introduce a dedicated user_roles table if SQLite foreign-key compatibility makes a rebuild unsafe. The selected migration must preserve user IDs, password hashes, account status, sessions, audit foreign keys, and all existing records.

AuthFilter will load the active user and operational role on every request. CurrentUserContext will carry both user ID and role for the request lifetime. A shared backend authorization component will enforce role checks in every service/use case; hiding controls in React is not authorization. Account suspension continues to revoke access through the existing session lookup and will be audited.

## Submission aggregate
Use a submission header plus immutable versions:
- report_submissions: ID, farm ID, author ID, report type, farm scope (farm/house/batch), current version, lifecycle status, assigned manager, idempotency key, and server timestamps.
- report_submission_versions: submission ID, monotonically increasing version, typed report payload JSON, event/observation date, optional late-submission reason, author, and server receipt timestamp. Submitted versions are immutable.
- report_submission_checks: version, stable check code, PASSED/WARNING/BLOCKED outcome, overrideability, structured explanation, and execution timestamp.
- report_submission_comments: submission/version, author, comment, and server timestamp.
- report_submission_events: append-only actor-attributed lifecycle, assignment, decision, override, and merge events.
- report_submission_links: source submission/version to resulting official record type and ID. This is provenance, not a second farm ledger.

Payload JSON is only a transport/snapshot format. Each supported report type gets a typed DTO, validator, and explicit merge adapter that calls the existing domain service. Do not expose generic table names or SQL dispatch. The merge adapter must not bypass domain invariants or create duplicate ledgers.

## Lifecycle and concurrency
Use explicit backend transitions: DRAFT, SUBMITTED, PENDING_REVIEW, FLAGGED, BLOCKED, CHANGES_REQUESTED, APPROVED, MERGING, MERGED, REJECTED. WITHDRAWN remains out of first release. Each transition uses an expected version/status predicate and checks affected-row count. Review decisions bind to an exact immutable version. A unique merge operation key and a database transaction protect each submission from duplicate effects. Immediately before merge, rerun critical checks against current farm state. All official domain writes, provenance links, workflow events, and audit events commit or roll back together.

A returned submission is edited by its author by creating a new version; prior versions, comments, checks, and decisions remain intact. Merged submissions are never edited. Corrections to resulting official records use the existing transaction-specific reversal/correction workflows.

## Permissions
- WORKER: own drafts, own submissions/history, create/submit supported reports, and submit emergency alerts. No farm-wide report or review access.
- ADMINISTRATOR: own reports and user/account administration. No other users' report visibility and no review/approval/merge access.
- MANAGER: own reports plus farm-wide submissions/reports, review, assignment, decisions, overrides, merge, schedule/threshold configuration, and emergency response. No user/role administration.
- Self-approval and self-merge are denied even for Managers.
- Assignment never grants a role. Disabled users retain historical drafts/submissions; managers can still see pending work.
- Authorization must be checked in service methods as well as at controller boundaries, with tests that call APIs directly.

## Emergency alerts and schedules
Emergency alerts are a separate incident aggregate and never write official farm records. They store reporter, observed time, receipt time, affected scope, category, reporter-selected severity, description, evidence references, acknowledgement, assignment, response, and resolution events. Manager notification is immediate and independent of the review queue. Any official update is linked to a normal submission.

Reporting schedules and mortality/emergency thresholds are farm configuration records. They start unset. No schedule or numeric threshold is hardcoded. Missing, explicit zero, no activity, and unknown remain distinct.

## Attachments and backup
Evidence metadata is associated with an immutable submission version and uploader. Files are stored under the existing local application data directory with strict allow-listed MIME/extensions, size limits, generated storage names, and backup/restore integration. No executable files. Backups include workflow rows and evidence files as one recoverable snapshot.

## Delivery phases
1. Role migration, request identity/authorization, and submission/version/audit persistence.
2. My Reports draft persistence and explicit online submission.
3. Manager Review Centre, assignment, comments, return/reject/approve.
4. Backend checks, duplicate/conflict detection, late-report policy, and override audit.
5. Atomic, idempotent merge adapters for existing supported domain services.
6. Emergency alerts, manager notifications, schedules, and missing-report views.
7. Evidence storage, backup/restore, and correction provenance.
8. End-to-end authorization, concurrency, rollback, offline, and regression acceptance tests.

Each phase must include backend and renderer tests plus manual steps against a disposable database. Existing farm records and calculations must be regression-tested. No phase is complete based solely on a successful build.
