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

A BROILER batch may enter SOLD only when its calculated current bird population is zero. Population records cannot be added to a non-ACTIVE batch.


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
\n## Feed management\n\nFeed type endpoints:\n- `GET /api/v1/feed/types`\n- `POST /api/v1/feed/types`\n- `POST /api/v1/feed/types/{id}/archive`\n\nPurchase and inventory endpoints:\n- `POST /api/v1/feed/purchases`\n- `GET /api/v1/feed/inventory?asOf=YYYY-MM-DD`\n\nBatch feed endpoints:\n- `POST /api/v1/feed/batches/{batchId}/usage`\n- `GET /api/v1/feed/batches/{batchId}/cost?asOf=YYYY-MM-DD`\n\nFeed quantities accept decimal input with at most three decimal places and are stored as integer thousandths of the configured unit. The usage/purchase unit must match the configured feed type unit.\n\nPurchasing feed records the original purchase cost and creates one `FEED` expense. Usage consumes inventory by FIFO and does not create another expense. Usage cost is added once to the batch attributable bird-cost pool.\n

## Daily farm operations

Daily operational views are exposed per batch:

- POST /api/v1/batches/{id}/daily-records
- GET /api/v1/batches/{id}/daily-records/{date}
- GET /api/v1/batches/{id}/daily-records?from=YYYY-MM-DD&to=YYYY-MM-DD

A daily record request contains the business date, optional notes, and water-container entries. Water entries reference configured farm container sizes and record a count, not a fake precise volume.

The daily response derives birds from the authoritative population engine, mortality and culling from that day's population events, and feed from that day's feed-usage records. Water is returned as container size × count plus a calculated total container capacity in configured units.

Mortality and culling remain separate population event types. Their requests support reason and notes. Population records continue to be validated by the existing population engine.

## Health management

Health endpoints:
- `POST /api/v1/batches/{id}/health`
- `GET /api/v1/batches/{id}/health`

A health record contains `recordDate`, `conditionProblem`, `description`, and `action`.

Drug endpoints:
- `POST /api/v1/batches/{id}/drugs`
- `GET /api/v1/batches/{id}/drugs`

A drug record contains `recordDate`, `drug`, positive `quantity`, integer minor-unit `costMinor`, and `reason`. Each drug record creates exactly one `DRUGS` expense linked to the drug record.

Vaccination endpoints:
- `POST /api/v1/batches/{id}/vaccinations`
- `GET /api/v1/batches/{id}/vaccinations`

A vaccination record contains `recordDate`, `vaccine`, `dose`, positive `quantity`, and optional `notes`. Vaccination history is separate from health records and does not create an expense automatically.

Health, drug, and vaccination writes require an ACTIVE batch and cannot be dated before batch placement.


## Layer egg production

Egg endpoints are available only for LAYER batches.

### Egg collection

POST /api/v1/batches/{id}/eggs/collections

Request fields: recordDate, good, cracked, notes.

GET /api/v1/batches/{id}/eggs/collections?asOf=YYYY-MM-DD

Collection quantities are individual eggs. Good and cracked are stored separately.

### Egg inventory

GET /api/v1/batches/{id}/eggs/inventory?asOf=YYYY-MM-DD

Returns goodCollected, crackedCollected, goodSold, goodRemaining, and totalCollected.

Cracked eggs never increase goodRemaining and cannot be sold.

### Egg sales

POST /api/v1/batches/{id}/eggs/sales

Request fields: recordDate, customer, crates, pricePerCrateMinor.

The configured farm default crate size is used at the time of sale. The database stores the resulting individual egg quantity and the crate size used for that sale, preserving historical meaning if the farm later changes its default crate size.

GET /api/v1/batches/{id}/eggs/sales?asOf=YYYY-MM-DD

The backend validates the complete historical good-egg ledger, including back-dated sales, so a new sale cannot make a later historical balance negative.


## Broiler production

Broiler production endpoints are available only for BROILER batches.

### Weight records

- POST /api/v1/batches/{id}/broiler/weights
- GET /api/v1/batches/{id}/broiler/weights?asOf=YYYY-MM-DD

Request fields: recordDate, sampleQuantity, totalWeightKg, optional notes.

The backend converts total weight to integer grams. Average weight and weight gain are calculated server-side.

### Growth and FCR

- GET /api/v1/batches/{id}/broiler/growth?asOf=YYYY-MM-DD

Returns latest average weight, cumulative live-weight gain, FCR, and the ordered growth trend.

FCR is calculated only by the backend:

FCR = feed consumed (kg) / live-weight gain (kg)

The known test vector is 400 kg feed and 200 kg live-weight gain, producing FCR 2.0. Only feed usage whose configured unit is kg contributes to this calculation.

### Bird sales

- POST /api/v1/batches/{id}/broiler/sales
- GET /api/v1/batches/{id}/broiler/sales?asOf=YYYY-MM-DD

Request fields: recordDate, quantity, pricePerBirdMinor, customer.

A bird sale creates a SOLD population event and is never recorded as mortality. When a sale leaves zero current birds, the backend transitions the batch to SOLD and subsequent ordinary writes are rejected.

## Stage 13: Pricing and margins

Pricing settings:
- `GET /api/v1/pricing/settings`
- `PUT /api/v1/pricing/settings` with `{ "targetMarginPercent": 25 }` or null

Broiler pricing preview:
- `GET /api/v1/pricing/batches/{batchId}?quantity=50&asOf=YYYY-MM-DD`

The response contains actual attributable cost, target/working margins, and backend-calculated target/working price per bird. Pricing is currently Broiler-only.

Broiler sale requests may include `confirmedBelowTarget: true`. If the configured target exists and the proposed sale margin is below it, the backend returns `BELOW_TARGET_CONFIRMATION_REQUIRED` unless explicit confirmation is supplied. The target is not lowered. Confirmed below-target sales and working-margin changes are audited.
