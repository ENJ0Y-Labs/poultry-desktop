# Authentication flow and request contract

## Product rule

First-run setup creates the initial local ADMINISTRATOR account. The backend supports WORKER, ADMINISTRATOR, and MANAGER roles through the authoritative `user_roles` table; existing OWNER accounts migrate to ADMINISTRATOR. Public registration and account switching remain unavailable. First-run owner setup and normal sign-in are distinct states, not two links into a generic registration form.

## Status-driven UI

1. The renderer calls `GET /api/v1/auth/status` before requesting protected account or farm data.
2. If `data.setupRequired` is true, show **Create owner account**. This form collects full name, username, optional email, and password, calls `POST /api/v1/auth/setup`, then signs in with the new username. The resulting account has the ADMINISTRATOR role.
3. If `data.setupRequired` is false and `data.authenticated` is false, show **Sign in** only. This form collects username-or-email and password. It must not expose owner setup fields or a sign-up link.
4. If `data.authenticated` is true, load protected account and farm data.

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

- **WORKER:** can use own-account endpoints and `/api/v1/submissions`; cannot call direct farm-ledger, audit, or account-administration APIs.
- **ADMINISTRATOR:** can manage user accounts and roles, and can access account activity history. Administrator status alone does not grant access to another user's report content or Manager review actions.
- **MANAGER:** can use farm operations and farm-wide audit history. Account and role administration is denied.

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
