# Database Schema

Stage 1 establishes the farm foundation. Flyway owns all schema changes and migrations are forward-only.

## Implemented Stage 1 tables

### `farms`
Stores the local farm identity and basic operating configuration.

- `id`: UUID stored as TEXT primary key.
- `name`: required.
- `location`: optional display location.
- `timezone`: defaults to `Africa/Lagos`.
- `currency`: three-letter currency code, defaults to `NGN`.
- `status`: `ACTIVE` or `ARCHIVED`.
- `created_at`, `updated_at`: technical timestamps.

### `users`
Stage 1 supports one primary owner/operator.

- Email is unique and case-insensitive.
- Passwords are stored only as hashes.
- Role is constrained to `OWNER`.
- Status is `ACTIVE` or `SUSPENDED`.

### `houses`
A house/pen belongs to a farm.

- Code and name are unique within a farm.
- Historical references are preserved by archiving rather than deleting.
- A house cannot be moved to another farm through the database contract.

### `suppliers`
Stores suppliers or other traceable sources used by the farm.

- `supplier_type` is `SUPPLIER` or `SOURCE`.
- Records can be archived without destroying purchase history.

### `batches`
Represents a Layer or Broiler flock.

- Human-readable batch `code` is globally unique.
- `batch_type` is `LAYER` or `BROILER`.
- `placement_date` is the farm business date.
- `initial_bird_count` is positive.
- Money is stored in integer minor units.
- Status is `ACTIVE`, `SOLD`, or `CLOSED`.
- The database does not store `current_birds`; population is calculated from records.
- Batch code generation is supported by `batch_code_sequences` and must occur inside the creation transaction.

### `bird_purchases`
Preserves original bird purchase history.

- Quantity must be positive.
- Unit and total costs are integer minor units.
- Purchase date is stored as a business date.
- Supplier/source is retained where applicable.

### `audit_logs`
Append-only audit foundation.

Stores actor, timestamp, action, entity, optional farm/batch scope, reason, and JSON snapshots of changed state. Application code must not expose update/delete operations for this table.

### `farm_settings`
Stores one set of farm-level defaults.
- Default egg crate size defaults to 30 eggs.
- Default water container size is optional and must be one of the configured sizes.

### `water_container_sizes`
Stores configurable water container sizes for the farm. Sizes are archived rather than destroyed so configuration history is preserved.

### `batch_code_sequences`
Supports transactional Layer/Broiler batch-code generation by year.

The service layer owns sequence allocation. It must allocate the number and create the batch in one short transaction.

## Relationships

`farm -> houses -> batches`

`farm -> suppliers -> bird_purchases`

`batch -> bird_purchases`

`users -> audit_logs`

`batches -> audit_logs`

## Planned later-stage tables

These are intentionally not created yet because later production stages have not begun:

`egg_production`, `egg_sales`, `weight_records`, `bird_sales`, `inventory`, `customers`, and `settings`.

Those tables will be introduced through separate forward-only migrations when their stage begins.

## Constraints

The schema uses `NOT NULL`, `CHECK`, `UNIQUE`, foreign keys, and indexes for foundational invariants. Service-layer validation remains responsible for cross-record rules that SQLite cannot express safely, such as population balance and batch-code allocation.

Never edit an applied migration. Add a new migration instead.


## Stage 6: Bird population events

`bird_population_events` stores population-changing facts instead of a mutable `current_birds` total.

Supported event types:
- `MORTALITY`
- `CULLING`
- `SOLD`
- `TRANSFER_IN`
- `TRANSFER_OUT`

Each event stores:
- batch
- farm business date
- positive quantity
- optional reference batch for transfers
- optional reason
- technical creation timestamp

Indexes support batch/date historical calculations and transfer references.

The authoritative population formula is:

`initial birds - mortality - culling - sold + transfers in - transfers out`

Population is calculated for an `asOf` business date. Events after that date are ignored.

Reduction events are rejected when their quantity exceeds the birds available immediately before that event date. This also protects historical reports when a record is entered for an earlier date.

Transfers are recorded as an atomic outbound event on the source batch and inbound event on the target batch. The source population is validated before either event is committed.

No `current_birds` column is stored.

## Stage 7: Bird cost events

`bird_cost_events` stores dated attributable costs that are added to a batch after the original bird purchase.

Supported event types:
- `ADDITIONAL_COST`
- `TRANSFER_IN_COST`

Amounts are integer minor units. A transfer-in cost references the source batch so the transferred birds carry the source cost into the target batch.

The original bird purchase remains in `batches.original_purchase_cost_minor` and is also recorded in `bird_purchases` with quantity, supplier/source, purchase date, integer unit cost, and total cost.

Batch creation requires a purchase total that divides evenly into whole minor units per bird. This prevents a stored unit price from becoming an approximation.

No floating-point monetary values are stored or used by the cost engine.
\n## Stage 8: Feed management\n\n### `feed_types`\nConfigurable feed definitions scoped to the farm. Each type declares its display unit and whether it applies to LAYER, BROILER, or BOTH. Types are archived rather than deleted.\n\n### `feed_purchases`\nStores immutable feed purchase facts: feed type, supplier, purchase date, quantity in integer thousandths of the configured unit, unit, and total cost in integer minor currency units. A purchase also creates one `FEED` expense.\n\n### `feed_usage`\nStores feed consumption facts by batch, feed type, date, and quantity. Consumption is not a second expense. Its FIFO cost is added once to the batch attributable bird-cost pool.\n\n### `expenses`\nThe common expense ledger currently supports `FEED`, `DRUGS`, and `OTHER`. Feed purchase rows reference their corresponding expense.\n\nFeed inventory is derived from purchase quantities minus usage quantities. Remaining lot cost is derived using FIFO and the original purchase price. No mutable inventory balance is stored.\n

## Stage 9: Daily farm operations

### daily_records
Stores the operational daily-record header for a batch and business date. It contains notes and technical timestamps. One record is allowed per batch/date.

Bird count, mortality, culling, and feed values are not duplicated into this table. They are derived from their authoritative event/usage records when a daily record is read.

### water_usage
Stores configurable water-container size references and the number of containers recorded for a daily record. Total water is calculated as container capacity × container count.

The bird_population_events table now also stores optional notes. Mortality and culling remain separate event types and retain their reason/notes independently.

## Stage 10: Health management

### health_records
Stores batch-scoped health observations: business date, condition/problem, description, action, and technical timestamps.

### drug_records
Stores batch-scoped drug records with drug name, positive quantity, business date, integer minor-unit cost, reason, and technical timestamp. Each drug record creates one linked `DRUGS` expense in the shared `expenses` ledger.

### vaccination_records
Stores batch-scoped vaccination history with vaccine, business date, dose, positive quantity, optional notes, and technical timestamp.

Health and vaccination are operational records. Drug cost is the accounting event and is recorded once in `expenses`.


## Stage 11: Layer egg management

### egg_collections
Stores batch-scoped egg production as individual eggs. Good and cracked quantities are separate, with optional notes and technical creation timestamp.

### egg_sales
Stores batch-scoped sales of good eggs. sold_eggs is the authoritative individual-egg quantity. crate_size preserves the configured crate size used at sale time. price_per_crate_minor and total_amount_minor are integer minor-unit money values.

Cracked eggs are intentionally absent from the sales table because they are not sellable.


## Stage 12: Broiler production

### weight_records

Stores Broiler sample-weighing facts:
- batch
- business date
- sample quantity
- total sample weight in integer grams
- optional notes
- technical creation timestamp

Average weight and growth are derived and are not stored as mutable totals.

### bird_sales

Stores Broiler bird-sale facts:
- batch
- business date
- quantity sold
- price per bird in integer minor currency units
- total sale amount in integer minor currency units
- customer
- technical creation timestamp

A corresponding SOLD population event remains the authoritative population change. The sale table preserves the commercial details.

Broiler batches become SOLD when all current birds have been sold. Once SOLD, ordinary Broiler production and population writes are rejected. Reopening remains the existing exceptional audited lifecycle action.

## Stage 13: Pricing and margins

### farm_settings additions
- `target_margin_percent`: optional configurable target margin percentage, initially null.
- `working_margin_percent`: optional latest/working margin reference, initially null.

### pricing_margin_history
Append-only pricing/margin change history. It records target changes and working-margin increases with references and reasons. The general `audit_logs` table also records operator-visible pricing changes and below-target confirmations.

Pricing calculations are derived from the existing bird-cost and bird-sale records. No stored sale price is treated as an authoritative future cost.

## Stage 14: Expenses

The existing `expenses` table remains the shared financial ledger.

Stage 14 adds:

- `batch_id TEXT REFERENCES batches(id)` nullable
- index on `(farm_id, batch_id, occurred_date)`

A null `batch_id` means farm-level expense. A non-null `batch_id` associates the expense with that batch.

Initial categories remain:
- `FEED`
- `DRUGS`
- `OTHER`

The migration backfills `batch_id` for existing `DRUGS` expenses generated from `drug_records`.

New general expense records use null `reference_type` and `reference_id`. Automatically generated feed/drug expenses retain their subsystem references.


## Stage 15: Customers and sales

### customers
Farm-scoped reusable customer records:
- `name` required
- `phone` optional
- `notes` optional
- technical created/updated timestamps

Customers are retained for historical sale references.

### sales
The common commercial sales ledger stores:
- farm
- batch
- customer
- business sale date
- `sale_type`: `EGG` or `BROILER`
- quantity
- commercial unit
- integer minor-unit unit price
- integer minor-unit total
- type-specific reference type and ID
- technical creation timestamp

The common ledger does not replace `egg_sales` or `bird_sales`. Those tables retain their type-specific production rules and authoritative facts. A common sale row is created in the same transaction as the corresponding type-specific sale.

Stage 15 also adds nullable `customer_id` references to `egg_sales` and `bird_sales`. Existing customer names are backfilled into farm-scoped customer records and existing sales are copied into the common ledger.
