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

These are intentionally not created yet because the repository is still in Stage 1:

`daily_records`, `mortality_records`, `culling_records`, `feed_types`, `feed_purchases`, `feed_usage`, `water_usage`, `health_records`, `drug_records`, `vaccination_records`, `egg_production`, `egg_sales`, `weight_records`, `bird_sales`, `inventory`, `customers`, `expenses`, and `settings`.

Those tables will be introduced through separate forward-only migrations when their stage begins.

## Constraints

The schema uses `NOT NULL`, `CHECK`, `UNIQUE`, foreign keys, and indexes for foundational invariants. Service-layer validation remains responsible for cross-record rules that SQLite cannot express safely, such as population balance and batch-code allocation.

Never edit an applied migration. Add a new migration instead.
