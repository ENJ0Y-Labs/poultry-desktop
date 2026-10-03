# API

All application endpoints are under `/api/v1`.

Controllers are intentionally thin. Services own validation, authorization, calculations, transactions, persistence rules, and audit writes.

Successful application responses use:

```json
{
  "ok": true,
  "data": {}
}
```

Application errors use:

```json
{
  "ok": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message"
  }
}
```

## Farm

- GET /farm
- POST /farm
- PUT /farm
- GET /farm/settings
- PUT /farm/settings
- GET /farm/houses
- POST /farm/houses
- PUT /farm/houses/{id}

## Batches

- GET /batches
- POST /batches
- GET /batches/{id}
- POST /batches/{id}/sold
- POST /batches/{id}/reopen
- GET /batches/{id}/dashboard
- GET /batches/{id}/population
- POST /batches/{id}/mortality
- POST /batches/{id}/culling
- POST /batches/{id}/bird-sales
- POST /batches/{id}/transfers
- GET /batches/{id}/costs
- POST /batches/{id}/costs

## Daily operations

- POST /batches/{id}/daily-records
- GET /batches/{id}/daily-records
- GET /batches/{id}/daily-records/{date}

## Feed

- GET /feed/types
- POST /feed/types
- POST /feed/types/{id}/archive
- POST /feed/purchases
- GET /feed/inventory
- POST /feed/batches/{id}/usage
- GET /feed/batches/{id}/cost

## Health

- GET /health
- POST /batches/{id}/health
- GET /batches/{id}/health
- POST /batches/{id}/drugs
- GET /batches/{id}/drugs
- POST /batches/{id}/vaccinations
- GET /batches/{id}/vaccinations

## Eggs

- POST /batches/{id}/eggs/collections
- GET /batches/{id}/eggs/collections
- POST /batches/{id}/eggs/sales
- GET /batches/{id}/eggs/sales
- GET /batches/{id}/eggs/inventory

## Broilers

- POST /batches/{id}/broiler/weights
- GET /batches/{id}/broiler/weights
- GET /batches/{id}/broiler/growth
- POST /batches/{id}/broiler/sales
- GET /batches/{id}/broiler/sales

## Suppliers

- GET /suppliers
- POST /suppliers
- POST /suppliers/{id}/archive

## Customers

- GET /customers
- POST /customers
- PUT /customers/{id}

## Sales

- GET /sales

## Expenses

- GET /expenses
- POST /expenses

## Inventory

- GET /inventory/items
- POST /inventory/items
- POST /inventory/items/{id}/archive
- GET /inventory/movements
- POST /inventory/items/{id}/movements

## Pricing

- GET /pricing/settings
- PUT /pricing/settings
- GET /pricing/batches/{id}

## Dashboard and attention

- GET /dashboard
- GET /attention
- GET /batches/{id}/dashboard

## Reports and audit

- GET /reports/farm
- GET /reports/batches/{id}
- GET /audit

## Authentication

- POST /auth/setup
- POST /auth/login
- POST /auth/logout
- GET /auth/me

Production requires an authenticated owner session. Development keeps authentication enforcement disabled so domain integration tests and local development can bootstrap the farm before the owner account is configured.

## Backup

- POST /backup
- POST /backup/validate

The renderer accesses these APIs through `app/renderer/src/services/api.js`. It does not access SQLite or backend internals directly.
