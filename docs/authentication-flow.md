# Authentication flow and request contract

## Product rule

First-run setup creates the initial local ADMINISTRATOR account. The backend supports WORKER, ADMINISTRATOR, and MANAGER roles through the authoritative `user_roles` table; existing OWNER accounts migrate to ADMINISTRATOR. Public registration and account switching remain unavailable. First-run owner setup and normal sign-in are distinct states, not two links into a generic registration form.

## Status-driven UI

1. The renderer calls `GET /api/v1/auth/status` before requesting protected account or farm data.
2. If `data.setupRequired` is true, show **Create owner account**. This form collects full name, username, optional email, and password, calls `POST /api/v1/auth/setup`, then signs in with the new username. The resulting account has the ADMINISTRATOR role.
3. If `data.setupRequired` is false and `data.authenticated` is false, show **Sign in** only. This form collects username-or-email and password. It must not expose owner setup fields or a sign-up link.
4. If `data.authenticated` is true, `data.user` contains the current user identity and database-resolved role. Use it to select the permitted workspace before loading protected data. The role is not accepted from renderer state or the session cookie.

## Login contract

Endpoint: `POST /api/v1/auth/login`

Request JSON:
- `loginIdentifier`: required non-blank string, either the owner's username or email address.
- `password`: required non-blank string.
- `rememberMe`: optional boolean; omitted/false means a session cookie, true requests a persistent cookie.

The frontend API client and Spring Boot `LoginRequest` DTO must keep these field names aligned. Do not send separate `username` or `email` fields to the login endpoint. Username and email are both values of `loginIdentifier`.

The backend validates the request at the API boundary. Invalid blank credentials are rejected with HTTP 400; the frontend should catch blank credentials before making the request and display a clear inline error. Successful login returns the standard `{ ok: true, data: ... }` envelope and sets the server-managed HTTP-only session cookie.

## Browser autofill

Controlled React state is not guaranteed to receive change events when the browser autofills a form. On submit, read named controls from the actual form (for example, with `FormData`) and validate the submitted values. Keep input `name` attributes aligned with the form fields and preserve regression coverage for autofill without React change events.

## Regression coverage

- Frontend: username and email each submit as `loginIdentifier`; blank credentials do not call the login API; autofilled DOM values are submitted; owner setup and sign-in controls remain distinct.
- Backend: username and email both authenticate through the same login request DTO; blank login credentials are rejected at the REST boundary.
- Continue running the full frontend and backend suites in CI. Do not claim manual packaged-Electron/browser cookie checks from automated test results alone.


## Operational roles and authorization

Roles are resolved from `user_roles` on every authenticated session lookup. Do not trust a role supplied by the renderer or cache a role in the session cookie.

- **WORKER:** can use own-account endpoints, limited farm reference data for report forms, own submissions, and comments on own submissions. Direct farm-ledger, audit, account-administration, and review-queue APIs are denied.
- **ADMINISTRATOR:** preserves the existing farm-owner operational workspace and can manage user accounts and roles. The Administrator can see only their own submissions and cannot access Manager review, cross-user submission detail, warning overrides, decisions, or merge actions.
- **MANAGER:** can use farm operations, farm-wide audit and submission/review APIs, including assignment, decisions, warning overrides, and merge. Account and role administration is denied.

The issue's capability matrix conflicts with its repeated role-specific rules and User Administration section about Administrator visibility/review. The implementation follows the repeated detailed rule: Administrator manages access but is not a report reviewer and cannot inspect other users' submissions. Do not silently broaden that role.

Administrator account endpoints:
- `GET /api/v1/admin/users`
- `POST /api/v1/admin/users`
- `PATCH /api/v1/admin/users/{id}/role`
- `PATCH /api/v1/admin/users/{id}/status`
- `POST /api/v1/admin/users/{id}/password-reset`

Account creation, role changes, suspension/reactivation, and password resets are audited. Role changes and password resets revoke the target user's sessions. Suspending an account revokes all sessions. The last active Administrator cannot be demoted or suspended.

## Report submission persistence

- `POST /api/v1/submissions` creates a persisted draft and version 1.
- `PUT /api/v1/submissions/{id}` appends a new immutable version while the report is a draft or has been returned for changes.
- `POST /api/v1/submissions/{id}/submit` submits the exact current version.
- `GET /api/v1/submissions` lists only the authenticated author's submissions.
- `GET /api/v1/submissions/{id}` returns the author's versions and append-only event history.

Submission versions and lifecycle events are append-only at the SQLite layer. Submission payloads must be JSON objects and are limited to 1 MB. A user who requests another author's submission receives the same not-found response as for an unknown identifier.


## Report submission and review API

Report author endpoints:
- `GET /api/v1/submissions/references`: active batch and feed references only.
- `GET /api/v1/submissions`: author's submissions only.
- `POST /api/v1/submissions`: creates a server draft using a required `Idempotency-Key`; key reuse with different content returns HTTP 409.
- `PUT /api/v1/submissions/{id}`: appends an immutable version to an editable draft or returned report.
- `POST /api/v1/submissions/{id}/submit`: freezes the current version and runs backend validation.
- `GET /api/v1/submissions/{id}`: returns only the author's own versions, checks, comments, events, decisions and resulting ledger links.
- `POST /api/v1/review/submissions/{id}/comments`: author may comment only on their own submission.

Manager review endpoints:
- `GET /api/v1/review/queue`
- `GET /api/v1/review/managers`
- `GET /api/v1/review/submissions/{id}`
- `POST /api/v1/review/submissions/{id}/assign`
- `POST /api/v1/review/submissions/{id}/comments`
- `POST /api/v1/review/submissions/{id}/checks/{checkId}/override`
- `POST /api/v1/review/submissions/{id}/decision`
- `POST /api/v1/review/submissions/{id}/merge` (requires a unique `Idempotency-Key`)
- `POST /api/v1/review/submissions/{id}/corrections/weight` (Manager-only; corrects a merged weight report using a required reason and replacement measurement)

Submission checks produce PASSED, WARNING or BLOCKED results. Warnings explicitly marked overrideable require a separate Manager override with a reason. Blocked reports cannot be approved. Approval applies to the exact submitted version and does not itself update official records.

Merge re-runs checks against current farm data, then invokes existing domain services inside one transaction. The merge transaction records a link from the submission version to each official record. A failure rolls back the entire merge; a retry returns the existing merged result instead of applying the change twice.

Workers and Administrators cannot access the Manager queue or decision/merge endpoints by calling URLs directly. The renderer's role-scoped navigation is a usability boundary; backend role checks and author-scoped repository queries are the security boundary.
