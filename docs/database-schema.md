# Database schema

SQLite schema changes are managed by Flyway. Migrations are forward-only and currently run through V20.

## Design rules

- UUID-style identifiers are stored as SQLite TEXT values.
- Money is stored as integer minor currency units.
- Quantities use integer representations where the domain requires precision.
- Foreign keys are enabled.
- Historical records are retained.
- Archivable definitions use status rather than destructive deletion.
- Derived balances are calculated from authoritative records where practical.
- Audit history is append-only.
- Cross-record business rules are enforced by services.

## Core farm tables

### `farms`

Farm identity and core configuration.

Key data includes name, location, timezone, currency, status and timestamps.

### `users`

Farm users and authentication data. Passwords are stored only as password hashes. Current application role is OWNER, with ACTIVE/SUSPENDED account status.

### `houses`

Farm houses/pens. Names and codes are farm-scoped and historical houses are archived rather than deleted.

### `suppliers`

Suppliers and traceable sources used for purchases.

### `batches`

Layer or Broiler flock records. Stores initial bird count and original purchase cost. Current bird population is derived from population events rather than stored as a mutable total.

### `bird_purchases`

Original bird purchase facts, including quantity, purchase date and integer minor-unit costs.

### `batch_code_sequences`

Transactional batch-code sequence state.

## Population and costing

### `bird_population_events`

Authoritative population changes:

- MORTALITY
- CULLING
- SOLD
- TRANSFER_IN
- TRANSFER_OUT

The current population formula is:

`initial - mortality - culling - sold + transfers in - transfers out`.

### `bird_cost_events`

Dated additional and transfer-in costs attached to batches.

### `feed_types`

Farm-scoped feed definitions, including display unit and Layer/Broiler applicability. Feed types are archived instead of deleted.

### `feed_purchases`

Immutable feed purchase lots. A feed purchase also creates one corresponding FEED expense.

### `feed_usage`

Feed consumption by batch and date. Usage is costed through FIFO and does not create a second expense.

## Daily and health operations

### `daily_records`

One operational daily-record header per batch/date. It does not duplicate authoritative bird or feed totals.

### `water_usage`

Container-size references and recorded container counts.

### `health_records`

Batch health observations.

### `drug_records`

Drug usage facts. Each recorded drug cost creates one DRUGS expense.

### `vaccination_records`

Vaccination history.

## Layer production

### `egg_collections`

Good and cracked egg collection records.

### `egg_sales`

Sellable egg sales. Individual sold eggs are authoritative; the crate size used at sale time is retained so later crate-size settings cannot rewrite history.

## Broiler production

### `weight_records`

Sample quantity and total sample weight in grams. Average weight and growth are derived.

### `bird_sales`

Broiler bird sales. A corresponding SOLD population event remains the authoritative population change.

## Financial and commercial tables

### `farm_settings`

Farm operating defaults, including egg crate size, water defaults and pricing/margin settings.

### `pricing_margin_history`

Append-only target/working margin changes.

### `expenses`

Shared expense ledger. Expenses may be farm-level or batch-associated.

### `expense_categories`

Farm-scoped configurable expense categories. Default categories are FEED, DRUGS and OTHER. Categories can be archived without changing historical expense rows.

### `customers`

Reusable farm-scoped customer records.

### `sales`

Common commercial sales ledger. Type-specific egg and bird sales remain authoritative and are mirrored into this common ledger.

## Inventory

### `inventory_items`

Farm-scoped non-feed stock definitions. Categories include DRUG, VACCINE and SUPPLY.

### `inventory_movements`

Append-only inventory transactions. RECEIVE and ADJUST_IN increase stock; ISSUE, ADJUST_OUT and WASTE reduce it.

Feed inventory remains on the dedicated feed/FIFO path rather than being duplicated in the generic inventory ledger.

## Authentication, audit and settings

### `audit_logs`

Append-only material-change history containing actor, action, entity, scope, reason and snapshots where applicable. Database triggers reject UPDATE and DELETE.

### `user_sessions`

Session-token hashes and session state used by authentication.

### `application_settings`

One settings row per farm.

Current fields include:

- start page
- date format

### `backup_settings`

One settings row per farm.

Current fields include:

- automatic backup enabled/disabled
- backup directory
- backup interval in milliseconds

### `water_container_sizes`

Farm-configurable water-container sizes. Historical configuration entries are archived rather than destroyed.

## Relationships

Conceptually:

```
farm
 ├── users
 ├── houses
 │    └── batches
 │         ├── population events
 │         ├── cost events
 │         ├── feed usage
 │         ├── daily records
 │         ├── health/drugs/vaccinations
 │         ├── egg collections/sales
 │         ├── weights/bird sales
 │         └── batch expenses
 ├── suppliers
 ├── customers
 ├── feed types/purchases
 ├── expense categories
 ├── expenses
 ├── inventory items/movements
 ├── sales
 ├── farm settings
 ├── application settings
 └── backup settings
```

## Migration history

The repository currently contains V1 through V20.

Broad progression:

- V1: schema marker
- V2: farm foundation
- V3: farm settings
- V4: batch indexes
- V5: population events
- V6: bird cost events
- V7: feed management
- V8: daily operations
- V9: health management
- V10: Layer egg management
- V11: Broiler production
- V12: pricing and margins
- V13: expenses
- V14: customers and sales
- V15: inventory
- V16: append-only audit enforcement
- V17: sessions
- V18: operational query indexes
- V19: performance indexes
- V20: settings management and configurable expense categories

Never edit an applied migration. Add V21 or later for future schema changes.
