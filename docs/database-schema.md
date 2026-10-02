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

`daily_records`, `mortality_records`, `culling_records`, `feed_types`, `feed_purchases`, `feed_usage`, `water_usage`, `health_records`, `drug_records`, `vaccination_records`, `egg_production`, `egg_sales`, `weight_records`, `bird_sales`, `inventory`, `customers`, `expenses`, and `settings`.

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