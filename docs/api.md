# API

Base prefix: `/api/v1`.

## Health

`GET /api/v1/health`

A healthy response proves all three foundation layers are available:

```json
{
  "ok": true,
  "status": "UP",
  "database": "UP",
  "schema": "VALID"
}
```

The endpoint executes a SQLite query and verifies that Flyway's schema history table and the application metadata table exist.

If the database is unavailable, the endpoint returns HTTP 503 with `database: DOWN`. If the database responds but the expected schema tables are missing, it returns HTTP 503 with `schema: INVALID`.

The backend is loopback-only. Development uses `localhost:<port>`; production uses `127.0.0.1:<port>`.

Feature endpoints will be added with their Stage 1 domain services.


## Farm management

- `POST /api/v1/farm` creates the single local farm profile.
- `GET /api/v1/farm` reads the active farm profile.
- `PUT /api/v1/farm` updates the farm profile.
- `GET /api/v1/farm/settings` reads crate and water defaults.
- `PUT /api/v1/farm/settings` updates the default crate size and configurable water-container sizes.
- `GET /api/v1/farm/houses` lists houses/pens.
- `POST /api/v1/farm/houses` creates a house/pen.
- `PUT /api/v1/farm/houses/{id}` updates or archives a house/pen.

The default egg crate size starts at 30 eggs. Water container sizes are configurable per farm. Houses/pens are archived rather than deleted when no longer active.


## Batch management

- `GET /api/v1/batches` lists batches for the local farm.
- `GET /api/v1/batches/{id}` reads one batch.
- `POST /api/v1/batches` creates a new Layer or Broiler batch.
- `POST /api/v1/batches/{id}/sold` moves a BROILER batch from ACTIVE to SOLD.
- `POST /api/v1/batches/{id}/reopen` reopens a SOLD batch when a non-blank reason is supplied.

Batch creation generates the human-readable code transactionally. The year comes from the placement date, and numbering is independent for Layer and Broiler batches.

Examples:

`L-2026-001`

`L-2026-002`

`B-2026-001`

A batch is created as ACTIVE. SOLD is terminal for ordinary writes. Reopening is an exceptional action and is audited with the supplied reason and timestamp.

The current Stage 5 `/sold` action is intentionally limited to BROILER batches. Full verification that all birds have been sold will be connected to the later bird-sales/event records, because those records are not yet part of the schema.


## Bird population

`GET /api/v1/batches/{id}/population?asOf=YYYY-MM-DD` returns the authoritative population breakdown for the requested business date. If `asOf` is omitted, the current local business date is used.

Population event endpoints:
- `POST /api/v1/batches/{id}/mortality`
- `POST /api/v1/batches/{id}/culling`
- `POST /api/v1/batches/{id}/bird-sales`
- `POST /api/v1/batches/{id}/transfers`

Mortality, culling, and bird-sale requests contain `eventDate`, positive `quantity`, and optional `reason`.

Transfer requests contain `targetBatchId`, `eventDate`, positive `quantity`, and optional `reason`. A transfer creates matching outbound and inbound events atomically.

The backend rejects a reduction that exceeds the population available immediately before the event date. This includes back-dated records. The renderer must display the returned API error rather than calculating a replacement value locally.


A BROILER batch may enter the terminal `SOLD` lifecycle only when its calculated current bird population is zero. Population records cannot be added to a non-ACTIVE batch.


## Bird cost accounting

`POST /api/v1/batches` now requires `purchaseCostMinor` as an integer minor-unit amount. The purchase total must divide evenly into whole minor units per bird. The backend records the original purchase in `bird_purchases`.

`GET /api/v1/batches/{id}/costs?asOf=YYYY-MM-DD` returns the historical carried-cost breakdown.

`POST /api/v1/batches/{id}/costs` records an additional attributable cost:

```json
{
  "eventDate": "2026-01-05",
  "amountMinor": 200000,
  "reason": "Applicable bird cost"
}
```

Additional cost dates cannot precede the batch placement date. Cost writes are rejected for non-ACTIVE batches.

The cost engine uses integer arithmetic and a deterministic weighted-average carried-cost pool. Cost additions on a business date are applied before population changes on that date. Sale, mortality, culling, and transfer-out birds receive their proportional attributable cost. Transfer-out cost is carried into the target batch as a transfer-in cost in the same transaction.
