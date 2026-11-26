# Database schema

SQLite schema changes are managed by Flyway. Migrations are forward-only and currently run through V35.

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

Farm users and authentication data. Passwords are stored only as password hashes. The legacy `users.role` column remains OWNER for upgrade compatibility; `user_roles` is authoritative for WORKER, ADMINISTRATOR, and MANAGER permissions. Existing OWNER accounts migrate to ADMINISTRATOR. Account status remains ACTIVE/SUSPENDED.

### `houses`

Farm houses/pens. Each house has an explicit `house_type` (`PERMANENT` or `TEMPORARY`); type is never inferred from its code. Names and codes are farm-scoped and historical houses are archived rather than deleted. Occupancy is derived from active batch assignments. V28 assigns legacy rows the documented `PERMANENT` default without inspecting house codes; administrators can reclassify them.

### `suppliers`

Suppliers and traceable sources used for purchases.

### `batches`

Layer, Broiler, or Turkey flock records. Stores initial bird count and, when recorded, original purchase cost. Status includes ACTIVE, SOLD, CLOSED, and CANCELLED. CANCELLED rows remain in history and audit views but are excluded from active operations. `original_purchase_cost_minor IS NULL` means the historical purchase cost is unknown/unrecorded; a value of `0` means a genuine zero-cost purchase. Current bird population is derived from population events and physical-count adjustments rather than stored as a mutable total. population_count_status is ESTIMATED or VERIFIED; new and migrated batches default to ESTIMATED until physically counted.

### `batch_create_requests`\n\nFarm-scoped idempotency ledger for batch creation. It stores the idempotency key, request fingerprint, resulting batch ID, and request timestamp. A retry with the same key and payload returns the original batch; reusing a key with a different payload is rejected.\n\n### `bird_purchases`

Original bird purchase facts, including quantity, purchase date and integer minor-unit costs. Existing-stock batches with unknown purchase details have no bird-purchase row; do not synthesize one.

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

`initial - mortality - culling - sold + transfers in - transfers out + physical-count adjustments`.

Population events must not predate the placement date of the affected batch. A transfer must be on or after both the source and target batch placement dates. When queried before placement, a batch has zero live birds; its immutable initial count remains the starting baseline for dates on or after placement. Farm-level as-of population totals include only batches placed on or before the selected date.

### `bird_population_adjustments`

Append-only physical-count reconciliations. Each row stores the effective date, previous calculated count, new physical count, signed difference, required reason, authenticated user ID, and server timestamp. The difference is included in chronological population calculations but remains separate from mortality, culling, sales, and transfer events. A successful adjustment changes batch confidence to VERIFIED; a later explicit provisional flag changes it back to ESTIMATED with an audit record. Count adjustments do not create a purchase cost or mortality/culling expense; existing carried bird cost remains with the batch.

### `bird_cost_events`

Dated additional and transfer-in costs attached to batches.

### `feed_types`

Farm-scoped feed product definitions, including product name, optional brand, packaging name, optional standard package weight in kilograms, display unit, kilogram conversion, and descriptive metadata. Unknown package weights and conversions remain NULL; never assume a standard bag size. V30 adds brand and packaging metadata without rewriting historical purchases or usage. V33 makes feed name plus brand the unique product identity within a farm; the same feed name can exist under different brands, while blank/NULL brand is one unbranded identity. FeedService trims and normalizes values before duplicate validation; the database index remains exact to preserve legacy case-variant rows. Editing descriptive identity fields preserves the feed type ID and all linked purchases, opening stock, usage, expenses, and FIFO history. Feed types are archived instead of deleted. The legacy `applicable_type` column remains for compatibility; current applicability is authoritative in `feed_type_applicability`.

### `feed_type_applicability`

One row per supported bird type for each feed definition. A feed type must have at least one applicability row. Supported values are `LAYER`, `BROILER`, and `TURKEY`; any non-empty combination is valid. The V32 migration maps legacy `BOTH` to all three types and maps each legacy single type to itself. Feed applicability is never inferred from a product name.

### `feed_purchases`

Immutable feed purchase lots. A feed purchase also creates one corresponding FEED expense.

### `feed_usage`

Feed consumption by batch and date. Usage is costed through FIFO and does not create a second expense.

### `feed_opening_balances`

Append-only opening stock snapshots for feed already on hand before the application's purchase ledger begins. They are not purchases and create no supplier, expense, or cash-flow records. Opening quantities have no historical valuation unless supported by source records; do not assign a fabricated zero or estimated cost.

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

## Meat-bird production

### `weight_records`

Sample quantity and total sample weight in grams. Average weight and growth are derived.

### `bird_sales`

Broiler bird sales. A corresponding SOLD population event remains the authoritative population change.

## Financial and commercial tables

### `farm_settings`

Farm operating defaults, including egg crate size, water defaults, water unit display label, and pricing/margin settings. The water unit label defaults to `litres` and is presentation-only; changing it does not convert or rewrite existing water measurements.

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

### `user_roles`

One authoritative operational role per user: WORKER, ADMINISTRATOR, or MANAGER. Existing owner accounts are seeded as ADMINISTRATOR. Role changes record the actor and timestamps; session lookups join this table so role changes take effect immediately.

### `user_sessions`

Session-token hashes and session state used by authentication. Session lookups require an ACTIVE account and read its current role assignment.

### `report_submissions`

Farm-scoped report draft and lifecycle header. Stores author, report type, legacy and workflow lifecycle status, current/submitted/decision versions, assignment, event date, late reason, idempotency key and request hash, merge operation key, and server timestamps. Repository methods scope author access; Manager review queries are farm-wide and role-guarded.

### `report_submission_versions`

Append-only report payload snapshots. Each save creates a new version containing report type, title, JSON payload, author, and server timestamp; database triggers reject UPDATE and DELETE. Submission and decision records point to exact versions.

### `report_submission_events`

Append-only actor-attributed submission lifecycle history with optional version reference, structured details, and server timestamp. Database triggers reject UPDATE and DELETE.

### report_submission_comments

Append-only comments associated with a submission and version. Worker/Administrator access is author-scoped; Managers can comment across the farm.

### report_submission_assignments

Append-only reviewer assignment and reassignment history. Assignment does not grant permissions beyond the Manager role.

### report_submission_decisions

Immutable whole-report decisions bound to a specific version. Multiple historical decisions may exist for a version if a later merge recheck returns it to review; only the current valid lifecycle transition can proceed.

### report_submission_check_runs and report_submission_checks

Each submission or pre-merge validation pass has an immutable check-run record and immutable individual Passed/Warning/Blocked checks. Only the latest check run for the submitted version governs approval/merge.

### report_submission_warning_overrides

Append-only Manager authorizations for individual overrideable warnings. Each record stores the exact check/version, Manager, reason, and timestamp. Duplicate overrides are constrained.

### report_submission_links

Links a successfully merged submission version to the official domain records created by the existing farm services. These links are written in the same transaction as the official records and final MERGED state.

### `application_settings`

One settings row per farm. V22 rebuilds this table with a CHECK constraint aligned to all nine supported renderer pages, preserving existing settings and timestamps.

Current fields include:

- start page, restricted to the canonical renderer pages: `dashboard`, `flocks`, `daily-work`, `feed-stock`, `health`, `production`, `sales-money`, `reports`, and `settings`
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

The repository contains forward-only migrations through V36, including independent Turkey support, population confidence/adjustments, feed opening balances, multi-type feed applicability, idempotent batch creation, operational roles, report version history, review workflow, immutable checks, and merge provenance.

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
- V15: inventory and feed usage reasons
- V16: append-only audit enforcement
- V17: sessions
- V18: operational query indexes
- V19: performance indexes
- V20: settings management and configurable expense categories
- V21: optional feed kilogram conversion factors
- V22: supported start-page values
- V23: farm water unit label
- V24: username login
- V25: transaction reversal records
- V26: zero egg collections
- V27: independent Turkey batch, feed applicability, and sales support
- V28: explicit permanent/temporary house classification and occupancy index
- V29: estimated/verified population confidence and auditable physical-count adjustments
- V30: feed brand and packaging metadata
- V33: brand-aware feed product identity, preserving existing feed type IDs and linked history\n- V34: idempotent batch creation and audited cancellation lifecycle
- V35: operational roles and immutable report submission versions/history
- V36: review comments, assignments, decisions, check runs/results, warning overrides, submission idempotency, merge operation keys, and official-record provenance
- V31: append-only feed opening balances
- V32: normalized multi-type feed applicability

Never edit an applied migration. Add a new version greater than the current highest version for future schema changes.


## Auditable transaction reversals (planned by ADR)

The approved correction design requires a forward-only migration adding a transaction reversal/correction relationship record. It will identify the original domain transaction, required reason, authenticated actor, server UTC action timestamp, optional replacement transaction reference, and creation metadata. A uniqueness constraint prevents more than one successful reversal of an original transaction. Type-specific foreign-key limitations must be validated in domain services. Original business rows and append-only audit rows remain intact.

This schema is not permission to implement a generic table-name dispatcher. Domain services remain responsible for transaction-specific validation and atomic recalculation. See `decisions/2026-10-26-auditable-transaction-corrections.md`.
