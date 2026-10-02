# AGENTS.md — Poultry Management Desktop

Instructions for AI coding agents and humans working in this repository. Read this file and `docs/` before changing anything. If code, conventions, commands, or domain rules change, update this file and the relevant documentation in the same change.

## 1. Product

A **local-first poultry farm management system for layer and broiler farms**, delivered as a Windows Electron desktop app and usable fully offline.

Core loop:

**record what happened → calculate what is happening → show what needs attention → produce reports.**

Priority order:

1. Correct records and calculations
2. Never lose data
3. Fast, simple data entry for farm workers
4. Clear batch-level and farm-level visibility
5. Dashboard polish

A boring screen with correct poultry numbers beats a beautiful dashboard showing agricultural fiction.

**Current stage: 1 — Farm foundation.**

Do not build later-stage features unless the owner explicitly changes the stage.

## 2. Non-negotiables

1. Derived state is calculated, never manually stored or maintained.
2. Each formula has exactly one implementation in `src/shared/domain` and is tested.
3. Money is integer minor units. Never use floating-point money.
4. Transactions are voided, not deleted. Important changes are audited.
5. The renderer has no Node.js, filesystem, or database access.
6. Main-process IPC validates every input.
7. Backups use SQLite `db.backup()` or `VACUUM INTO`, never a raw copy of a live DB.
8. No network calls, telemetry, cloud services, CDN assets, or local HTTP server in normal operation.
9. Never edit a shipped migration. Add a new migration.
10. Never weaken/delete a test to make it pass.
11. Never silence TypeScript with `any`, `@ts-ignore`, or equivalent.
12. Never commit `*.db`, `*.db-wal`, `*.db-shm`, backups, or real farm data.
13. Keep one local farm data model. Do not unnecessarily split the farm into separate databases/accounts.
14. Stage 1 has one primary owner/operator account. Multi-user roles can be added later.
15. Opening a new batch creates a new permanent batch record. It does not overwrite an old batch.
16. The broiler terminal status is **SOLD**, not COMPLETED.
17. Reopening a SOLD batch is an exceptional, audited action and requires a reason.

## 3. Stack

| Concern | Choice |
|---|---|
| Desktop | Electron |
| UI | React + TypeScript, strict |
| Database | SQLite + better-sqlite3 |
| Validation | Zod |
| Charts | Recharts |
| Tests | Vitest |
| Build | electron-vite + electron-builder |
| Package manager | npm |

Do not add Flask, Express, an ORM, cloud services, telemetry, or another process without owner approval.

## 4. Repository architecture

`docs/` is the source of truth.

`src/main`
- Electron lifecycle
- IPC handlers
- services/use cases
- repositories/SQL
- database/migrations
- backup/restore
- filesystem/printing/export

`src/preload`
- narrow typed contextBridge API

`src/renderer`
- React UI only
- no Node, SQLite, Electron main imports, or filesystem access

`src/shared`
- pure domain calculations
- enums/capabilities
- Zod schemas
- IPC contracts

Architecture:

`React → preload → IPC → service → repository → SQLite`

IPC handlers validate input, resolve the acting user from the main-process session, call a service, and return:

`Result<T> = { ok: true, data } | { ok: false, error: { code, message } }`

Services own transactions, business rules, invariants, calculations, and audit writes. Repositories contain SQL only.

Electron security:
- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true`
- never expose `ipcRenderer` directly
- never expose `fs` or arbitrary IPC
- strict CSP
- block uncontrolled navigation/window opening

## 5. Farm and batch model

Hierarchy:

**Farm → House/Pen → Batch/Flock → records**

Every batch has:
- unique human-readable code
- type: LAYER or BROILER
- placement/opening date
- initial bird count
- house/pen
- supplier/source where applicable
- original bird purchase cost where applicable
- status
- audit history

Examples:
- `L-2026-001`
- `B-2026-003`

Generate batch codes inside the creation transaction.

Use one capability function, `flockCapabilities(type)`, to decide which modules exist. Do not scatter `if (type === 'LAYER')` across the codebase.

Every batch:
- Overview
- Daily Records
- Feed
- Mortality
- Culling
- Health
- Drugs/Vaccination
- Sales
- Expenses
- Reports

Layers additionally:
- Egg Production
- Egg Quality
- Egg Inventory

Broilers additionally:
- Weight
- Growth
- FCR
- Bird Sales
- SOLD lifecycle

## 6. Batch lifecycle

Broiler batches become **SOLD when all birds have been sold**.

Do not call this COMPLETED.

A SOLD batch is normally locked against ordinary writes.

If a correction is necessary, explicitly reopen it and audit:
- who reopened it
- when
- why
- what changed

Examples of reasons:
- accidental sale entry
- missing transaction
- wrong bird count
- data-entry error

The audit reason is part of the permanent history.

## 7. Bird population

The database stores events/transactions. The application calculates state.

Never store `current_birds`.

Current birds as of a date:

`placed - mortality - culling - sold + transfers_in - transfers_out`

Validate the running balance on every affected date, not only the final total. A back-dated mortality must not make a later transaction invalid.

Sales reduce live birds but are not mortality.

Every historical metric accepts an `asOf` date.

Undefined calculations return `null` and the UI displays **—**, never `0`, `NaN`, or `Infinity`.

## 8. Dashboards

There are two dashboard levels.

### Main farm dashboard

The first page is a complete farm-wide summary.

It should show, where data exists:
- current total birds
- current layers
- current broilers
- mortality
- feed information
- eggs
- current income/revenue
- expenses
- profit
- active batches
- relevant inventory/stock
- other tested farm KPIs

Every figure must come from an authoritative service or tested domain calculation.

### Batch dashboard

Every batch has its own smaller dashboard containing only that batch's data:
- initial birds
- current birds
- mortality
- culling
- sold birds
- feed used
- feed cost
- expenses
- revenue
- profit
- production
- relevant layer/broiler metrics
- status
- attention items

**Batch report = one batch. Farm report = all relevant batches/farm records combined.**

Never duplicate calculation logic between the two.

## 9. Daily records

When a new batch is opened, preserve its opening information as historical data.

Operational records belong to the relevant batch.

Correct mistakes by editing/voiding the underlying record and auditing the change. Never overwrite a calculated total.

Business dates use `YYYY-MM-DD` in farm-local time. UTC ISO timestamps are used for technical timestamps.

Never use `new Date('YYYY-MM-DD')` for business-date calculations.

## 10. Feed and inventory

Feed is purchased in the units actually used by the farm, including bags/kg. The purchase records:
- quantity
- purchase date
- supplier
- original purchase price
- available quantity

If 50 bags cost ₦500,000:
- inventory receives 50 bags
- the purchase expense/cost is ₦500,000
- later consumption of 20 bags is an allocation, **not another ₦200,000 expense**

When feed is opened/used, record:
- feed lot/type
- batch
- quantity used
- date

### FIFO

Feed cost allocation is **FIFO: first in, first out**.

Oldest available feed is consumed first.

Each purchase remembers its original price.

Example:
- 20 bags × ₦8,000
- 30 bags × ₦10,000
- batch uses 25 bags

Allocated cost:
- 20 × ₦8,000
- 5 × ₦10,000

Do not replace historical purchase prices with current prices.

### Cost follows birds

The cost of feed and other attributable bird costs must be carried by the birds.

If five birds each carry ₦100 and four are sold, the remaining bird keeps its remaining attributable cost.

If the remaining bird later receives ₦200 of additional attributable feed cost, that bird's carried cost becomes ₦300.

The general algorithm must:
1. identify the cost carried by available/live birds
2. allocate cost to birds sold/lost as required
3. leave the remaining cost with the remaining birds
4. add later attributable costs to the remaining population
5. remain explainable and deterministic

A sold bird must carry its attributable cost. Otherwise profit is meaningless.

Example: a bird sold for ₦900 with ₦700 attributable cost produces ₦200 before other applicable expenses.

Mortality and culling must also be handled by an explicit tested cost-allocation rule. Do not silently delete their cost.

## 11. Water containers

Feed quantity is measured in the farm's normal purchase/use units.

Water consumption does not need fake precision.

The configurable part is the water-container size. The farm can define sizes such as 25 and 75 units, and record the number of containers rather than pretending exact water volume was measured.

## 12. Egg management

Store egg quantities internally as **individual eggs**.

For operator convenience, the UI may accept:
- loose eggs
- crates
- combinations of crates and loose eggs

Default crate/tray size is **30 eggs**, but it is configurable in Settings.

### Collection

Record separately:
- good eggs
- cracked eggs

Total collected can be derived as:

`good + cracked`

### Cracked eggs

Cracked eggs cannot be sold.

Keep them in the historical record. Do not force them into the sellable balance.

Track:
- good collected
- cracked collected
- good sold
- good remaining

It is valid for good remaining to be zero while cracked eggs remain recorded.

### Egg sales

Eggs are sold by crate, including half crates.

Examples:
- 1 crate
- 0.5 crate
- 2.5 crates

Store/normalize the underlying quantity as individual eggs using the configured crate size.

For a sale, record:
- crates/quantity sold
- price per crate
- sale total

No unnecessary egg-size field is required for the current system.

## 13. Drugs and health costs

For accounting, medications and vaccines use one expense category:

**Drugs**

Do not force the farm to separate medication and vaccination financially.

Operational health/vaccination records can remain distinct where needed for production history.

## 14. Expense scope

Keep the initial expense model deliberately simple.

Do not add separate categories for:
- electricity
- water
- transportation
- equipment
- repairs
- miscellaneous transportation/equipment costs

Only include expense categories that reflect what the farm actually needs at this stage.

## 15. Suppliers and bird purchase history

Supplier records are important for tracing purchases.

For broilers, preserve:
- supplier/source
- purchase date
- purchase quantity
- original cost per bird or equivalent purchase cost

Historical purchase price must remain attached to the original purchase/batch data.

Do not silently replace it with today's price.

## 16. Broiler pricing and target margin

Broiler pricing must be calculatable from actual bird costs.

The target margin is:
- configurable
- initially `null`
- not hard-coded

The latest relevant pricing/sales record becomes the basis for subsequent pricing calculations.

If a sale achieves a margin above the configured target, the working/latest margin reference may increase based on the actual result.

If a new sale is below the configured target, do not silently lower the target. Ask for confirmation before changing it.

Changes to target/working margin are audited.

If a recalculation is triggered by a new cost, bird count, feed allocation, or sale, calculate again from the underlying records rather than editing a stored derived price.

## 17. Money

Money is integer minor units. Never use JS floats for money.

Revenue:

`quantity × unit_price`

Profit:

`revenue - attributable costs - expenses`

Round only at the appropriate line-total boundary.

Profit must account for the attributable cost of sold birds/eggs and relevant expenses.

## 18. Core calculations

All formulas live once under `src/shared/domain/calculations`.

They are pure, tested, and reused by dashboards, reports, and exports.

Required formulas include:

| Metric | Definition |
|---|---|
| Current birds | placed − mortality − culling − sold + transfers in − transfers out |
| HDP % | eggs produced ÷ live hens × 100 |
| Livability % | living birds after mortality/culling ÷ placed × 100 |
| FCR | feed consumed ÷ live weight gain |
| Revenue | quantity × unit price |
| Profit | revenue − attributable costs − expenses |

FCR's exact production basis must be documented before implementation if it affects schema semantics.

Round for presentation only.

## 19. Audit trail

Audit every material create/update/void/restore/status/cost/stock/bird-count/money action.

An audit row contains:
- timestamp
- user
- action
- entity type and ID
- batch where applicable
- changed fields
- before/after values
- reason where required

Audit logs are append-only. Normal application code must never update/delete them.

Reopening a SOLD batch always records a reason.

## 20. Database rules

Use SQLite with:
- foreign keys ON
- WAL mode
- `synchronous = FULL`

Keep transactions short. Do not `await` inside a transaction.

Tables use snake_case plurals. TypeScript uses camelCase.

Use database constraints for:
- NOT NULL
- CHECK constraints
- foreign keys
- unique identifiers
- useful indexes

Transactions are voided rather than hard-deleted.

Reference data such as houses, suppliers, and customers is archived when historical references require preservation.

## 21. Migrations

Migrations are numbered, forward-only, and never edited after shipment.

A backup is created before migration.

Every schema migration updates `docs/database-schema.md`.

Tests apply all migrations to a clean database.

## 22. Backup and restore

The database lives on a user's PC, so backups are mandatory.

Use SQLite `db.backup()` or `VACUUM INTO`.

Never copy the live `.db` file.

Backup names:

`poultry-YYYY-MM-DD.db`

If another backup exists that day:

`poultry-YYYY-MM-DD-HHmm.db`

Automatic backups should default to retaining the last 30.

Backup locations can be user-selected, including USB drives. Missing destinations must fail gracefully.

Restore:
1. validate backup
2. run `PRAGMA integrity_check`
3. reject a schema version newer than the app
4. safety-backup current DB
5. close DB
6. replace DB
7. reopen DB
8. apply pending migrations if required

## 23. UI rules

The app is designed for farm workers.

Prioritize:
- keyboard-first entry
- logical Tab order
- Enter to save where appropriate
- today's date defaults
- last-used batch defaults
- visible units
- plain-language errors
- confirmation before voiding
- 1366×768 usability

Abbreviations such as HDP and FCR must have formula tooltips.

All null metrics display **—**.

The UI presents data. It does not calculate farm metrics itself.

## 24. Navigation

Main navigation:

- Dashboard
- Farm
  - Houses/Pens
  - Batches/Flocks
- Operations
  - Daily Records
  - Feed
  - Mortality
  - Culling
  - Health
  - Drugs/Vaccination
  - Water
- Production
  - Eggs
  - Weight & Growth
- Inventory
  - Stock
  - Purchases
  - Suppliers
- Commerce
  - Sales
  - Customers
  - Expenses
- Reports
- Settings

Batch-specific modules must follow `flockCapabilities`.

## 25. Testing

Calculations require tests for:
- zero
- one
- large values
- null denominators
- historical dates
- mortality/culling
- partial sales
- FIFO
- additional feed costs
- cost carried by remaining birds
- cost carried by sold birds
- egg cracking
- half-crate sales
- batch reopening
- farm/batch isolation

Use real in-memory SQLite and real migrations for service/repository integration tests. Do not mock database behavior.

Reference vectors:

`placed 5,000 − mortality 83 − culling 20 − sold 70 = 4,827`

`(5,000 − 83 − 20) / 5,000 × 100 = 97.94%`

`4,200 / 4,800 × 100 = 87.5% HDP`

`6,000 / 3,000 = 2.0 FCR`

`0 / 0 = null`

## 26. Delivery plan

### Stage 1 — Farm foundation

Build:
- farm setup
- houses/pens
- owner account
- supplier/source information needed for batch creation
- Layer/Broiler batch creation
- placement date and opening bird count
- original bird purchase cost where applicable
- batch codes
- current-bird calculation
- batch dashboard
- farm dashboard
- audit foundation
- pre-migration backups

Done when a Layer or Broiler batch can be opened and its dashboard and population are correct and traceable.

### Stage 2 — Daily operations

Build:
- daily records
- feed usage
- mortality
- culling
- water
- health
- drugs/vaccination
- weight

A worker must be able to record a full day using the keyboard, and invalid population changes must be rejected.

### Stage 3 — Production and money

Layers:
- eggs
- cracked eggs
- egg inventory
- configurable crate size
- egg sales

Broilers:
- weight
- growth
- FCR
- FIFO feed costing
- attributable bird cost
- bird sales
- SOLD lifecycle
- pricing/margin logic

Common:
- purchases
- inventory
- suppliers
- customers
- expenses
- revenue
- profit

Done when farm and batch totals reconcile with their source records.

### Stage 4 — Management

Build:
- expanded KPI dashboards
- reports
- charts
- exports
- alerts
- audit viewer
- backup/restore UI
- scheduled backups

Every report must use the same calculations as the batch/farm screens.

## 27. Working agreement

Before coding:
1. Read this file and relevant `docs/`.
2. Confirm the task belongs to the current stage.
3. If not, stop.
4. For schema semantics, formulas, IPC, architecture, permissions, or Electron security changes, write a short plan and get owner approval.
5. Record non-obvious decisions in `docs/decisions/`.
6. If a farm rule is unclear, ask the owner. Do not invent one.

Definition of done:
1. Schema + migration + documentation updated when needed.
2. Zod schema and IPC contract updated.
3. Service with transaction and invariants.
4. Audit behavior implemented where required.
5. Pure calculations implemented once and tested.
6. Keyboard-friendly UI with visible units.
7. Farm and batch dashboards remain consistent.
8. Typecheck passes.
9. Lint passes.
10. Tests pass.
11. Documentation is current.

Keep commits small and focused.

If code and documentation disagree, stop and flag it.

## 28. Owner decisions already made

These decisions are intentional and should not be changed casually:

- One primary owner/operator for now.
- Batch-level dashboards plus one farm-wide dashboard.
- Farm-wide dashboard is the first/front page.
- Batch reports contain only that batch; the main report combines the farm.
- Broiler terminal lifecycle state is SOLD when all birds are sold.
- Reopening requires an audited reason.
- Feed purchases are inventory and expense at purchase time; later batch consumption is allocation, not a second expense.
- Feed costing is FIFO.
- Original purchase price is preserved.
- Cost follows birds so sold birds carry attributable cost and remaining birds retain remaining cost.
- Water uses configurable container sizes rather than fake precision.
- Eggs are stored as individual units internally.
- UI supports crates and loose units.
- Default crate size is 30 and is configurable.
- Cracked eggs are recorded separately and are never sellable.
- Good eggs can reach zero remaining while cracked eggs remain recorded.
- Egg sales support half crates.
- Drugs cover medication and vaccines for expense classification.
- Electricity, water, transportation, equipment, and repairs are not separate expense categories in the current model.
- Broiler target margin is configurable and initially null.
- Margin updates use the latest relevant record, with confirmation before lowering the configured target.
- Farm and batch dashboards reuse the same tested calculations.

## 29. Guiding principle

This is farm software, not a spreadsheet wearing an Electron costume.

Record facts.

Preserve history.

Calculate state.

Explain every important number.

Never double-count a cost.

Never lose confirmed data.

Never hide corrections.

And never sacrifice poultry math for dashboard cosmetics.
