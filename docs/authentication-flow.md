# Authentication flow and request contract

## Product rule

Stage 1 supports one local primary owner/operator account. First-run owner setup and normal sign-in are different states of the application, not two links into a generic registration form. Account switching, public registration, and multiple accounts are not implemented; do not expose a sign-up or switch-account control that points to an unsupported endpoint.

## Status-driven UI

1. The renderer calls `GET /api/v1/auth/status` before requesting protected account or farm data.
2. If `data.setupRequired` is true, show **Create owner account**. This form collects full name, username, optional email, and password, calls `POST /api/v1/auth/setup`, then signs in with the new username.
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
