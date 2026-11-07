# Decision: Shared housing, shared feed, and traceable batch merging

**Date:** 2026-11-07  
**Status:** Design approved for phased implementation under [Issue #213](https://github.com/ENJ0Y-Labs/poultry-desktop/issues/213)  
**Release target:** Pomas v1.1.0

## Context

The current model is batch-centric and stores one `house_id` directly on each batch. A house can therefore be referenced by several batches at the database level, but the application does not have dated placement records, house capacity, placement-specific occupancy, or a reliable movement history. Updating a batch's setup currently overwrites its house reference.

The current feed ledger is also batch-only: `feed_usage.batch_id` is NOT NULL. FIFO inventory and valuation are calculated from `feed_purchases`, `feed_opening_balances`, and `feed_usage`. A shared-house feed record cannot be represented by inventing a batch split without corrupting batch costing.

The current population ledger is batch-scoped. Population is derived from initial birds, population events, and physical-count adjustments. Those existing records must remain authoritative and must not be duplicated by a second mutable occupancy counter.

This issue is an owner-authorized expansion beyond the current documented Stage 17 scope. The work is divided into phases. The desktop release version remains 1.0.3 until all v1.1.0 acceptance and verification requirements are complete.

## Decisions

### 1. House placement and occupancy

Introduce a dated `batch_house_placements` relation rather than treating `batches.house_id` as the complete placement history.

The proposed placement record contains:

- UUID primary key
- batch and house foreign keys
- positive `placement_quantity` snapshot
- `start_date` and nullable `end_date`
- server-generated `created_at`

For this release, one biological batch has at most one active house placement at a time. Multiple batches may have active placements in the same house. Splitting one biological batch across multiple houses is not included; it would require explicit flock-splitting semantics for every batch-scoped record and cost calculation.

Add nullable positive `capacity_birds` to houses. NULL means the existing house capacity is unknown or has not been configured. The migration must not invent capacities for existing houses.

Add a nullable `placement_id` foreign key to population events and physical-count adjustments. New population records are attached to the batch's active placement. The migration creates one historical placement for each existing batch, using its current house, original placement date, and initial bird count, then links that batch's existing population events and adjustments to it. This preserves the historical population ledger while allowing house occupancy to be derived per placement.

House occupancy is calculated from placement snapshots plus the population changes linked to each placement. It is never stored as an editable total. Batch population remains calculated by the existing population calculator. Movement is one transaction: validate the active batch and destination, calculate its population on the movement date, close the old placement, create the new placement with that population snapshot, update the legacy `batches.house_id` compatibility field, and append an audit record. A move affects only the selected batch.

The legacy `batches.house_id` field remains a compatibility projection during the v1.1.0 transition. New reads should use placement records as the authoritative relationship. It must not be independently editable after the placement workflow is introduced.

Capacity validation uses derived current occupancy plus the incoming placement quantity. An unset capacity is not treated as zero. A move or placement that would exceed a configured capacity is rejected; no negative or over-capacity occupancy is silently accepted.

### 2. Mixed-species occupancy

Species remains a property of each biological batch. No placement or house operation may rewrite it.

House detail and batch detail responses will include their active placements and species-specific counts. A house with more than one active batch type receives a prominent mixed-species warning. The warning is informational and must direct the operator to review veterinary and biosecurity requirements; it must not present mixed-species housing as recommended husbandry.

Feed applicability, medication, vaccination, health, growth, and production validation remain species-aware. A shared physical house does not make a feed or treatment applicable to every species in it.

### 3. Shared-house feed consumption

Keep one authoritative FIFO feed-consumption stream. Do not create a second feed stock ledger and do not represent shared consumption as multiple fabricated batch usages.

The preferred migration is a forward-only rebuild of `feed_usage` that preserves every existing row and identifier, makes `batch_id` nullable, adds nullable `house_id`, and enforces that exactly one target (batch or house) is present. Existing rows remain batch-targeted. A house-targeted row deducts its quantity once from the same FIFO stream as existing batch usages.

Add `feed_usage_allocations` as child records for later measured or estimated attribution. Each allocation references one house-targeted usage and one batch, stores a positive integer-thousandths quantity, the allocation method (`MEASURED` or `ESTIMATED`), and the estimate method where applicable. The allocation quantities must sum exactly to the parent usage quantity. An unallocated usage has no child allocations. The parent feed usage remains the inventory/FIFO event, so adding allocations later must never deduct stock again.

Measured allocation records actual quantities. Estimated allocation records an explicit operator-selected method and values. Bird-count proportion must never be selected automatically or described as biologically accurate. Unallocated quantity and cost remain at house level. Reports must distinguish measured, estimated, and unallocated amounts and disclose when batch cost completeness is limited.

A later allocation is an audited attribution change, not a new feed usage. It preserves the original parent usage and its FIFO lot consumption. The service must allocate the already-calculated cost to batches without replaying inventory deduction or creating a second expense. The exact integration with the existing bird-cost event model must be implemented and tested before enabling the allocation endpoint.

A house-level feed usage must validate the feed product's declared bird-type applicability against the batches it is intended to serve. Mixed housing does not relax applicability. Where the feed is intended for only one batch, record normal batch-level usage instead of misrepresenting it as shared-house consumption.

### 4. Batch merge and lineage

A merge is an explicit, confirmed domain operation. Housing together never merges biological batches.

Add an immutable merge operation record containing the resulting batch, effective date, reason, actor, and creation timestamp, plus source-to-result lineage rows containing each source batch and its contributed quantity. A source batch may participate in at most one successful merge. Source and result species must match, and all relevant batch/domain compatibility rules must be validated before writes.

The result is a new operational batch identity. Source batch records and their existing population, feed, health, vaccination, production, sales, expense, and audit history remain intact. Source batches are marked non-operational using the existing terminal `CLOSED` lifecycle plus an explicit merge-lineage relationship; the UI derives and displays the more specific “Merged” state from that relationship. The result must not inherit or duplicate source historical records.

The merge service must carry forward the source populations and attributable bird costs using explicit merge-specific cost events. It must not copy source events into the result or count both source and result populations in dashboards. The existing bird-cost calculator and lifecycle queries must be extended and tested before merge is exposed. Different species can never be merged into one biological batch.

### 5. Shared operational records

The first implementation must not force batch attribution when the affected flock is known only at house level. Shared mortality, culling, health events, and expenses require explicit house/shared attribution and population/cost semantics. These must be implemented only after placement identity is available and must update population exactly once.

No event may be divided between batches using a guessed ratio. If a reliable allocation is unavailable, preserve it at house/shared level and disclose the limitation in batch reports.

### 6. Audit and corrections

Placement creation, movement, capacity changes, shared feed usage, feed allocation, and merge operations are audited with actor, timestamp, reason where required, and before/after references.

Posted transactions remain subject to the existing reversal-plus-replacement correction policy. Do not edit or delete a posted feed usage, population event, allocation, placement, or merge to make history appear different. Any correction must preserve source relationships and rebuild derived calculations atomically. If a dependent ledger cannot be safely recalculated, reject the correction.

## Migration and rollout plan

The migration series on the implementation branch must continue after V33; never modify a shipped migration.

1. **Housing foundation:** add nullable house capacity, create dated placements, seed one placement per existing batch, and link existing population events and physical-count adjustments. Add migration tests starting from a V33 database with existing batches and population history.
2. **Placement services and UI:** create/list/move placements, validate capacity and available birds, derive occupancy, and display all batches in a house. Preserve the legacy house field as a synchronized compatibility projection.
3. **Mixed-species visibility:** expose species-specific occupancy and warnings in house/batch details and relevant dashboards.
4. **Shared feed ledger:** migrate the existing FIFO usage table without losing row identity, introduce house-targeted usage, and prove stock is deducted exactly once.
5. **Feed attribution:** add measured, estimated, and unallocated allocation records; implement audited later allocation without replaying FIFO or duplicate cost.
6. **Batch merge:** add merge operation/lineage, source lifecycle handling, merge-specific bird-cost carry-forward, and compatible-species validation.
7. **Shared operations and reporting:** add house/shared attribution where semantics are fully defined, then update dashboards, reports, exports, and attention items.
8. **Release hardening:** run all existing and new tests, migration/upgrade tests from v1.0.4, frontend lint/tests/build, backend verify, and packaging verification. Update the authoritative package version to 1.1.0 only at this final release phase.

Each phase is a reviewable commit or pull request. The implementation must document changed files, business rules, tests, exact mock-data steps, and evidence for population totals, FIFO stock/cost reconciliation, and data preservation.

## Required invariants

- A house can contain multiple active batches; a batch remains a distinct biological record.
- One batch movement cannot change another batch's placement or population.
- House occupancy is derived from placement snapshots and linked population changes.
- Configured capacity is never exceeded; unknown capacity remains unknown.
- Mixed species remain distinct and visibly warned.
- Every feed consumption quantity enters the FIFO stream exactly once.
- Later feed allocation changes attribution only; it does not change stock or purchase expense totals.
- Estimated allocations are always labelled estimates and retain their method.
- Unallocated feed remains house-level and is not fabricated into batch consumption.
- Merge sources remain historically accessible and are not double-counted with the result.
- Different species cannot be merged.
- Existing feed purchases, opening balances, usage, FIFO lot history, expenses, population events, and audit history survive migration unchanged.
- Existing tests are retained; no test is weakened or removed to obtain a passing build.

## Verification fixtures

Use disposable mock data only:

1. House 001 capacity 500; create Broiler A (200) and Broiler B (150) in that house. Occupancy is 350 and both batches remain distinct.
2. Move Broiler B to House 002. House 001 becomes 200; House 002 becomes 150; Broiler A is unchanged.
3. Place 50 turkeys and 200 broilers in one house. Total is 250, species counts remain separate, and the mixed-species warning is visible.
4. Record 25 kg as unallocated shared-house feed. FIFO stock decreases by exactly 25 kg and no batch receives an invented quantity.
5. Allocate the existing 25 kg later as measured 8 kg turkey and 17 kg broiler quantities. Stock does not decrease again; allocation audit is retained.
6. Record estimated allocation and confirm its method and estimated status appear in reports.
7. Reject a turkey-to-broiler merge. Merge two compatible broiler batches and verify source lineage, cost carry-forward, historical access, and no double-counted birds.
8. Record a shared-house mortality event and verify population is reduced exactly once without arbitrary source-batch attribution.
9. Upgrade a disposable v1.0.4 database and verify existing farm records, feed stock, FIFO costing, backups, and audit history.

