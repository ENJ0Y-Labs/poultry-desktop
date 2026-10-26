# API

The Spring Boot API is served under:

```
http://127.0.0.1:18942/api/v1
```

Production binds to localhost only.

Controllers return successful responses in this shape:

```json
{"ok":true,"data":{}}
```

Errors use:

```json
{
  "ok": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message"
  }
}
```

Boundary validation errors use HTTP 400. Domain, authorization, conflict, storage and internal failures are mapped by the central exception handling layer without exposing stack traces, credentials or sensitive filesystem details.

## Health

- GET `/health`

Used by Electron during startup.

## Farm and houses

- GET `/farm`
- POST `/farm`
- PUT `/farm`
- GET `/farm/settings`
- PUT `/farm/settings`
- GET `/farm/houses`
- POST `/farm/houses`
- PUT `/farm/houses/{id}`
- GET/POST house-alias endpoints under the house-alias controller

## Batches and population

- GET `/batches`
- POST `/batches`
- GET `/batches/{id}`
- POST `/batches/{id}/sold`
- POST `/batches/{id}/reopen`
- GET `/batches/{id}/population`
- POST `/batches/{id}/mortality`
- POST `/batches/{id}/culling`
- POST `/batches/{id}/bird-sales`
- POST `/batches/{id}/transfers`
- GET `/batches/{id}/costs`
- POST `/batches/{id}/costs`

## Daily operations

- POST `/batches/{id}/daily-records`
- GET `/batches/{id}/daily-records`
- GET `/batches/{id}/daily-records/{date}`

## Feed

- GET `/feed/types`
- POST `/feed/types`
- PUT `/feed/types/{id}/conversion` (body: `kgPerUnit`, positive number or `null` when unknown)\n- POST `/feed/types/{id}/archive`
- POST `/feed/purchases`
- GET `/feed/inventory`
- POST `/feed/batches/{id}/usage`
- GET `/feed/batches/{id}/cost`

## Health management

- POST `/batches/{id}/health`
- GET `/batches/{id}/health`
- POST `/batches/{id}/drugs`
- GET `/batches/{id}/drugs`
- POST `/batches/{id}/vaccinations`
- GET `/batches/{id}/vaccinations`

## Layer eggs

- POST `/batches/{id}/eggs/collections`
- GET `/batches/{id}/eggs/collections`
- POST `/batches/{id}/eggs/sales`
- GET `/batches/{id}/eggs/sales`
- GET `/batches/{id}/eggs/inventory`

## Broilers

- POST `/batches/{id}/broiler/weights`
- GET `/batches/{id}/broiler/weights`
- GET `/batches/{id}/broiler/growth`
- POST `/batches/{id}/broiler/sales`
- GET `/batches/{id}/broiler/sales`

## Suppliers and customers

Suppliers:

- GET `/suppliers`
- POST `/suppliers`
- POST `/suppliers/{id}/archive`

Customers:

- GET `/customers`
- POST `/customers`
- PUT `/customers/{id}`

## Expenses

- GET `/expenses`
- POST `/expenses`

Configurable expense categories:

- GET `/expense-categories`
- POST `/expense-categories`
- POST `/expense-categories/{id}/archive`

## Inventory

- GET `/inventory/items`
- POST `/inventory/items`
- POST `/inventory/items/{id}/archive`
- GET `/inventory/movements`
- POST `/inventory/items/{id}/movements`

## Pricing

- GET `/pricing/settings`
- PUT `/pricing/settings`
- GET `/pricing/batches/{id}`

## Dashboard and attention

- GET `/dashboard`
- GET `/attention`
- GET `/batches/{id}/dashboard`

Optional `asOf=YYYY-MM-DD` is supported where the service exposes historical calculations.

## Reports and exports

- GET `/reports/farm`
- GET `/reports/batches/{id}`
- GET `/reports/farm.csv`
- GET `/reports/batches/{id}.csv`
- GET `/reports/farm.pdf`
- GET `/reports/batches/{id}.pdf`

Reports reuse the same backend calculation services used by dashboards. CSV and PDF are export representations of those report results. PDF exports embed a locally installed Unicode font; font discovery supports Windows system/user fonts and common DejaVu Sans, Liberation Sans, and FreeSans directories on Linux and macOS.

## Audit

- GET `/audit?limit=100`

Audit history is read-only through the normal application API.

## Authentication and account

- POST `/auth/setup`
- POST `/auth/login`
- POST `/auth/logout`
- GET `/auth/status`
- GET `/auth/me`
- GET `/account`
- PUT `/account`

The desktop checks `/auth/status` before loading protected farm or account data. The status endpoint returns whether a valid session exists and whether the initial owner setup is still required; it does not expose account credentials or require authentication. If setup is required, the renderer creates the owner account and signs in. Otherwise it prompts the owner to sign in.

Production requires an authenticated owner session. The `poultry_session` cookie emitted by `POST /auth/login` uses `Path=/; HttpOnly; SameSite=None; Secure` and has no `Max-Age` or `Expires` attribute, so it is a browser-session cookie. The server-side session expires after 12 hours regardless of whether the browser retains the cookie. `HttpOnly` prevents renderer JavaScript from reading the token; `Path=/` scopes it to API paths on the loopback host; `Secure` is retained for Chromium secure-cookie handling on the local loopback API.

`SameSite=None` is intentional for the current supported origins: packaged Electron loads its renderer from a local `file://` origin, while browser development can load it from the Vite origin. Both make credentialed requests to the API at `http://127.0.0.1:18942`, which is a different origin/site context. `SameSite=Strict` can prevent the cookie from being sent in these contexts. The API must remain loopback-only; do not loosen cookie attributes or expose the API to remote origins to make another deployment mode work. This policy is shared across development, packaged Electron, and browser development rather than switching cookie security by environment.

`POST /auth/logout` invalidates the server-side session and returns a clearing cookie with the same `Path=/; HttpOnly; SameSite=None; Secure` scope plus `Max-Age=0`. Cookie-header unit tests verify these emitted attributes. Automated header tests do not replace a manual Chromium check in browser development and the packaged Electron app. `/auth/me` returns HTTP 401 without an active session; use `/auth/status` to check session state without an expected authorization error.

## Application and backup settings

- GET `/settings/application`
- PUT `/settings/application`
- GET `/settings/backup`
- PUT `/settings/backup`

Application settings currently include start page and date format. Backup settings include automatic-backup state, directory and interval.

## Backup

- POST `/backup`
- POST `/backup/validate`

The desktop restore replacement itself is deliberately not exposed as a renderer-controlled arbitrary file API. Electron owns the native restore workflow.

## Renderer API client

The renderer's API wrapper is:

```
app/renderer/src/services/api.js
```

It sends credentials with requests, translates failed responses into `ApiError`, and keeps the renderer independent from backend implementation details.
