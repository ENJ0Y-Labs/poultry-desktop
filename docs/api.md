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

- POST `/batches/{id}/daily-records` creates a day sheet or updates the existing batch/date sheet.
  - Send `notes: null` to preserve notes when editing water.
  - Send `waterContainers: null` to preserve water entries when editing notes.
  - Send a water-entry array to replace the day's water entries.
  - Concurrent creation attempts return a conflict that instructs the operator to reload.
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

Production requires an authenticated owner session. Authentication uses an HTTP-only, SameSite=Strict session cookie. `/auth/me` returns HTTP 401 without an active session; use `/auth/status` to check session state without an expected authorization error.

## Application and backup settings

- GET `/settings/application`
- PUT `/settings/application`
- GET `/settings/backup`
- PUT `/settings/backup`

Application settings include start page and date format. Supported start-page values are `dashboard`, `flocks`, `daily-work`, `feed-stock`, `health`, `production`, `sales-money`, `reports`, and `settings`. Any other value is rejected with HTTP 400. Backup settings include automatic-backup state, directory and interval.

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
