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
- POST `/feed/types/{id}/archive`
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

Reports reuse the same backend calculation services used by dashboards. CSV and PDF are export representations of those report results.

## Audit

- GET `/audit?limit=100`

Audit history is read-only through the normal application API.

## Authentication and account

- POST `/auth/setup`
- POST `/auth/login`
- POST `/auth/logout`
- GET `/auth/me`
- GET `/account`
- PUT `/account`

Production requires an authenticated owner session. Authentication uses an HTTP-only, SameSite=Strict session cookie.

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
