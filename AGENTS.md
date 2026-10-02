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

**Current stage: 11 — Layer production.**

Stage 8 feed management is implemented. Stage 9 daily farm operations is implemented. Stage 10 health management is implemented. Stage 11 adds Layer-only egg production, egg inventory, cracked-egg history, configurable crate conversion, and egg sales. Broiler production modules such as weight and growth remain future work.

Do not build later-stage features unless the owner explicitly changes the stage.

## 2. Non-negotiables

1. Derived state is calculated, never manually stored or maintained.
2. Each formula has exactly one implementation in the shared domain/calculation layer and is tested.
3. Money is integer minor units. Never use floating-point money.
4. Transactions are voided, not deleted. Important changes are audited.
5. The Electron renderer has no Node.js, filesystem, or database access.
6. Electron IPC validates every input.
7. The Spring Boot backend validates every request at the API boundary.
8. The Electron app communicates with Spring Boot through a REST API. Do not introduce a second backend API style.
9. Backups use SQLite `backup` functionality or `VACUUM INTO`, never a raw copy of a live DB.
10. The application must remain fully usable without internet access.
11. No telemetry, cloud dependency, CDN assets, or third-party network service in normal operation.
12. Never edit a shipped migration. Add a new migration.
13. Never weaken/delete a test to make it pass.
14. Do not introduce TypeScript into the frontend. The frontend is React + JavaScript.
15. Never commit `*.db`, `*.db-wal`, `*.db-shm`, backups, or real farm data.
16. Keep one local farm data model. Do not unnecessarily split the farm into separate databases/accounts.
17. Stage 1 has one primary owner/operator account. Multi-user roles can be added later.
18. Opening a new batch creates a new permanent batch record. It does not overwrite an old batch.
19. The broiler terminal status is **SOLD**, not COMPLETED.
20. Reopening a SOLD batch is an exceptional, audited action and requires a reason.

## 3. Stack

| Concern | Choice |
|---|---|
| Desktop shell | Electron |
| Frontend | React + JavaScript |
| Frontend modules | JavaScript/JSX |
| Backend | Java + Spring Boot |
| Backend API | Spring Boot REST API |
| Database | SQLite for local-first operation |
| Database access | Spring JDBC / repository layer |
| Schema migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Serialization | Jackson |
| Frontend charts | Recharts |
| Frontend tests | Vitest |
| Backend tests | JUnit 5 + Spring Boot Test |
| Desktop build | electron-vite + electron-builder |
| Frontend package manager | npm |
| Java build | Maven |

### Stack rules

- Do not add Flask.
- Do not add Express.
- Do not add TypeScript to the frontend.
- Do not add another backend framework.
- Do not add an ORM such as Hibernate/JPA unless the owner explicitly approves it.
- Do not add cloud services, telemetry, or another server/process without owner approval.
- Spring Boot is the backend source of truth for business rules and persistence.
- React is responsible for presentation and user interaction, not authoritative farm calculations.
- Keep the backend capable of running locally on the same PC as the Electron application.
- Prefer a packaged/local Spring Boot process launched and supervised by Electron for the desktop distribution.
- The user must not need to install or manually operate Java/Spring Boot separately in the production desktop app.
- Development may run Electron and Spring Boot as separate processes.

## 4. Architecture

The system has three application layers:

**Electron → React renderer → REST API → Spring Boot → SQLite**

### Electron

Electron owns:
- application lifecycle
- starting/stopping the local Spring Boot process
- desktop/window security
- native filesystem operations needed by the desktop shell
- backup/restore file selection where appropriate
- printing/export integration where appropriate
- secure IPC between Electron main and renderer

### React renderer

React owns:
- screens
- forms
- tables
- navigation
- loading/error states
- keyboard-first interaction
- displaying values returned by the backend

Use normal JavaScript and JSX files. Do not create `.ts` or `.tsx` frontend source files.

React must not:
- connect directly to SQLite
- access Node.js APIs
- implement authoritative farm calculations
- mutate database files
- bypass the Spring Boot API

### Spring Boot

Spring Boot owns:
- REST API
- request validation
- authentication/session state
- authorization
- services/use cases
- business rules
- domain calculations
- transaction boundaries
- audit writes
- repositories
- SQL/database access
- migrations
- backup coordination
- farm/batch invariants

Recommended package structure:

`backend/src/main/java/.../`

- `config/`
- `controller/`
- `dto/`
- `service/`
- `domain/`
- `repository/`
- `validation/`
- `audit/`
- `exception/`

Keep controllers thin. Business logic belongs in services/domain code. Repositories contain persistence logic.

### API flow

**React → API client → local Spring Boot REST endpoint → Controller → Service → Repository → SQLite**

The renderer must never bypass the API.

API responses should use a consistent envelope where useful:

`{ ok: true, data: ... }`

or

`{ ok: false, error: { code, message, details? } }`

Do not expose raw SQL/database exceptions to the UI.

## 5. Electron security

Use:

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true` where compatible with the application
- narrow typed `contextBridge`
- no direct `ipcRenderer` exposure
- no direct `fs` exposure
- strict CSP
- controlled navigation
- controlled window creation

The renderer communicates with the backend through the application's API client.

Spring Boot should bind only to localhost for the local desktop deployment.

Do not expose the local API to the LAN unless the owner explicitly changes the product security model.

## 6. Backend lifecycle

Electron is the desktop orchestrator.

Development:

- start Spring Boot locally
- start Electron/React locally
- Electron connects to the configured local API address

Production:

1. Electron starts.
2. Electron starts the packaged Spring Boot backend.
3. Spring Boot starts and validates the local database.
4. Electron waits for a health/readiness endpoint.
5. Electron opens the main window only when the backend is ready.
6. Electron owns backend shutdown when the application exits.
7. Backend failures must produce a clear application error rather than a blank UI.

The backend must expose a lightweight local health/readiness endpoint for Electron startup checks.

The health endpoint must not expose sensitive farm data.

## 7. Farm and batch model

Hierarchy:

**Farm → House/Pen → Batch/Flock → records**

Every batch has:
- unique human-readable code
- type: LAYER or BROILER
- placement/opening date
- initial bird count
- house/pen
- supplier/source where applicable
- original bird purchase cost in integer minor units
- status
- audit history

Examples:
- `L-2026-001`
- `B-2026-003`

Generate batch codes inside the creation transaction.

Use one capability function, `flockCapabilities(type)`, to decide which modules exist. Do not scatter type checks across the codebase.

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

## 8. Batch lifecycle

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

## 9. Bird population

The database stores events/transactions. The application calculates state.

Never store `current_birds`.

Current birds as of a date:

`placed - mortality - culling - sold + transfers_in - transfers_out`

Validate the running balance on every affected date, not only the final total. A back-dated mortality must not make a later transaction invalid.

Sales reduce live birds but are not mortality.

Every historical metric accepts an `asOf` date.

Undefined calculations return `null` and the UI displays **—**, never `0`, `NaN`, or `Infinity`.

## 10. Dashboards

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

Every figure must come from an authoritative Spring Boot service or tested domain calculation.

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

## 11. Daily records

When a new batch is opened, preserve its opening information as historical data.

Operational records belong to the relevant batch.

Correct mistakes by editing/voiding the underlying record and auditing the change. Never overwrite a calculated total.

Business dates use `YYYY-MM-DD` in farm-local time. UTC ISO timestamps are used for technical timestamps.

Do not parse business dates as UTC instants. Use `LocalDate` in Java for farm business dates.

## 12. Feed and inventory

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
3. leave the remaining cost with the remaining population
4. add later attributable costs to the remaining population
5. remain explainable and deterministic

A sold bird must carry its attributable cost. Otherwise profit is meaningless.

Example: a bird sold for ₦900 with ₦700 attributable cost produces ₦200 before other applicable expenses.

Mortality and culling must also be handled by an explicit tested cost-allocation rule. Do not silently delete their cost.


### Cost accounting

Money is always stored and calculated in integer minor units. For NGN, ₦500 is 50,000 kobo.

Initial bird purchase cost is required when a batch is opened and is preserved in both the batch opening data and `bird_purchases` history.

Bird cost uses a deterministic weighted-average carried-cost pool:
1. start with the batch purchase cost
2. apply dated attributable cost additions before population changes on that business date
3. when birds leave through sale, mortality, culling, or transfer-out, allocate the current carried pool proportionally to the birds leaving
4. subtract that attributable cost from the carried pool
5. when birds transfer in, their calculated source cost is added to the target cost pool
6. any remainder stays with the remaining birds

All arithmetic is integer-only. If a proportional allocation is not an exact number of minor units, the integer quotient is used and the remaining minor-unit balance stays with the remaining birds. If all remaining birds leave, the entire remaining cost is allocated so no kobo disappears.

Additional attributable costs are recorded as dated cost events. The cost engine is historical: an `asOf` date includes only cost and population events on or before that date.

## 13. Water containers

Water consumption does not need fake precision.

The configurable part is the water-container size. The farm can define sizes such as 25 and 75 units, and record the number of containers rather than pretending exact water volume was measured.

## 14. Egg management

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

## 15. Drugs and health costs

Health, drug, and vaccination records are batch-scoped operational history.

Health records store:
- date
- condition/problem
- description
- action

Drug records store:
- drug
- quantity
- date
- batch
- cost in integer minor units
- reason

Every drug record creates exactly one shared expense with category **DRUGS**. Drug usage does not create a second expense.

Vaccination records store:
- vaccine
- date
- batch
- dose
- quantity
- notes

Health and vaccination remain operationally separate. Vaccines do not create an expense automatically because the current requirement does not attach a cost to vaccination records.

## 16. Expense scope

Keep the initial expense model deliberately simple.

Do not add separate categories for:
- electricity
- water
- transportation
- equipment
- repairs
- miscellaneous transportation/equipment costs

Only include expense categories that reflect what the farm actually needs at this stage.

## 17. Suppliers and bird purchase history

Supplier records are important for tracing purchases.

For broilers, preserve:
- supplier/source
- purchase date
- purchase quantity
- original cost per bird
- original total purchase cost

Historical purchase price must remain attached to the original purchase/batch data.

Do not silently replace it with today's price.

## 18. Broiler pricing and target margin

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

## 19. Money

Money is integer minor units. Never use JS or Java floating-point types for money.

Use integer minor units for persistence and API DTOs. In Java, prefer `long` for monetary minor units.

Revenue:

`quantity × unit_price`

Profit:

`revenue - attributable costs - expenses`

Round only at the appropriate line-total boundary.

Profit must account for the attributable cost of sold birds/eggs and relevant expenses.

## 20. Core calculations

All formulas live once in the shared domain/calculation layer, preferably under the Spring Boot domain package.

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

The frontend must never become a second source of truth for these calculations.

## 21. API design

Use REST endpoints under a versioned API prefix such as `/api/v1`.

Keep endpoint naming resource-oriented.

Examples:
- `GET /api/v1/farm/dashboard`
- `GET /api/v1/batches`
- `POST /api/v1/batches`
- `GET /api/v1/batches/{id}`
- `GET /api/v1/batches/{id}/dashboard`
- `POST /api/v1/batches/{id}/mortality`
- `POST /api/v1/batches/{id}/feed-usage`
- `POST /api/v1/batches/{id}/eggs`
- `POST /api/v1/batches/{id}/sales`

Do not make controllers responsible for SQL or domain calculations.

Validate DTOs at the controller boundary using Jakarta Bean Validation.

For complex business rules, validate again in the service/domain layer.

Use explicit DTOs. Do not expose database entities directly as public API contracts.

API error responses must be stable, user-safe, and actionable.

## 22. Authentication and user model

Stage 1 has one primary owner/operator account.

The backend owns authentication and authorization.

Do not trust a user ID supplied by the renderer when determining the acting user.

If authentication is session-based, keep session state server-side and use a secure HTTP-only cookie where practical.

For local-only operation:
- bind the backend to localhost
- do not expose authentication/session endpoints to the LAN
- never store plaintext passwords
- use a strong password hashing algorithm supported by the selected Java security library
- audit important authentication/account actions

Multi-user roles and permissions are future work.

## 23. Audit trail

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

## 24. Database rules

Use SQLite for the local-first database.

Required SQLite behavior:
- foreign keys ON
- WAL mode where supported
- `synchronous = FULL`

Keep transactions short.

Use Spring-managed transactions around service operations. Do not perform long-running or blocking work inside database transactions.

Tables use snake_case plurals. Java uses camelCase.

Use database constraints for:
- NOT NULL
- CHECK constraints
- foreign keys
- unique identifiers
- useful indexes

Transactions are voided rather than hard-deleted.

Reference data such as houses, suppliers, and customers is archived when historical references require preservation.

## 25. Migrations

Use **Flyway** for schema migrations.

Migrations are numbered, forward-only, and never edited after shipment.

A backup is created before migration.

Every schema migration updates `docs/database-schema.md`.

Tests apply all migrations to a clean database.

Do not mix Flyway migration ownership with ad-hoc schema creation in application startup.

## 26. Backup and restore

The database lives on a user's PC, so backups are mandatory.

Use SQLite `backup` functionality or `VACUUM INTO` through a controlled backend service.

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
5. stop/close database access
6. replace DB
7. reopen DB
8. apply pending Flyway migrations if required
9. verify application health

Because the backend owns the database connection, restore must be coordinated through the Spring Boot lifecycle. The Electron renderer must never replace database files directly.

## 27. UI rules

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

The UI presents data returned by the backend. It does not own authoritative farm calculations.

## 28. Navigation

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

## 29. Testing

### Backend

Use:
- JUnit 5
- Spring Boot Test
- integration tests against a real temporary/in-memory SQLite database where supported
- real Flyway migrations
- service/repository integration tests
- controller/API tests for validation and error contracts

Do not mock database behavior when testing SQL, migrations, repository behavior, or transaction invariants.

### Frontend

Use:
- Vitest
- React testing tools where needed

Test UI behavior without duplicating backend business rules.

### Calculation tests

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

Reference vectors:

`placed 5,000 − mortality 83 − culling 20 − sold 70 = 4,827`

`(5,000 − 83 − 20) / 5,000 × 100 = 97.94%`

`4,200 / 4,800 × 100 = 87.5% HDP`

`6,000 / 3,000 = 2.0 FCR`

`0 / 0 = null`

## 30. Delivery plan

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
- Electron ↔ Spring Boot local process startup/health check

Done when a Layer or Broiler batch can be opened and its dashboard and population are correct and traceable.

### Stage 2 — Daily operations

Build:
- daily records
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

Feed management was moved to Stage 8. Bird cost accounting was moved to Stage 7.

### Stage 7 — Cost accounting

Build:
- integer minor-unit money model
- required bird purchase cost history
- attributable bird cost calculation
- additional attributable batch costs
- cost carried by remaining birds
- cost allocated to sold, mortality, culling, and transfer-out birds
- transfer cost carrying into the target batch
- historical `asOf` cost reporting
- tests for exact allocation and reconciliation

Done when purchase cost, attributable cost, carried cost, and reduction cost reconcile without floating-point arithmetic.

### Stage 8 — Feed management

Build:
- configurable Layer/Broiler/BOTH feed types with explicit units
- feed purchase records with original purchase price
- purchase-time feed inventory increase and FEED expense
- batch feed usage records
- FIFO consumption using oldest available purchase lots first
- historical original purchase prices preserved
- feed inventory and remaining-cost calculations
- batch feed consumed quantity and feed cost
- feed cost per current bird
- feed cost added to attributable bird cost exactly once
- tests for one purchase, multiple purchases, partial usage, multiple prices, exhausted lots, and FIFO ordering

Feed quantities are stored as integer thousandths of the configured unit so kg, bags, and fractional quantities remain exact without floating-point arithmetic.

Feed purchase and usage dates are chronological within each feed type. Backdating a purchase before existing usage or backdating usage before existing usage is rejected because doing so would change already-recorded FIFO attributable costs without rewriting historical bird-cost events.

Done when feed purchases increase inventory and record one FEED expense, feed usage consumes the oldest stock first, no usage creates a second expense, and feed cost reconciles with attributable bird cost.

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

## 31. Working agreement

Before coding:
1. Read this file and relevant `docs/`.
2. Confirm the task belongs to the current stage.
3. If not, stop.
4. For schema semantics, formulas, API contracts, architecture, permissions, or Electron/Spring security changes, write a short plan and get owner approval.
5. Record non-obvious decisions in `docs/decisions/`.
6. If a farm rule is unclear, ask the owner. Do not invent one.

Definition of done:
1. Schema + Flyway migration + documentation updated when needed.
2. API DTO/schema/contract updated.
3. Service with transaction and invariants.
4. Audit behavior implemented where required.
5. Pure calculations implemented once and tested.
6. Keyboard-friendly UI with visible units.
7. Farm and batch dashboards remain consistent.
8. Electron ↔ Spring Boot integration remains healthy.
9. Frontend tests pass.
10. Backend tests pass.
11. Frontend build/lint passes.
12. Backend build/tests pass.
13. Documentation is current.

Keep commits small and focused.

If code and documentation disagree, stop and flag it.

## 32. Owner decisions already made

These decisions are intentional and should not be changed casually:

- One primary owner/operator for now.
- Batch-level dashboards plus one farm-wide dashboard.
- Farm-wide dashboard is the first/front page.
- Batch reports contain only that batch; the main report combines the farm.
- Broiler terminal lifecycle state is SOLD when all birds are sold.
- Reopening requires an audited reason.
- Feed purchases are inventory and expense at purchase time; later batch consumption is allocation, not a second expense.
- Feed types are configurable and declare a unit plus applicable flock type.
- Feed quantities use integer thousandths of the configured unit.
- FIFO feed consumption preserves each purchase lot's original cost.
- Feed usage cost is added once to the batch's attributable bird-cost pool.
- Feed purchase/usage chronology is append-only per feed type so persisted attributable bird costs remain stable.
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
- Backend is Java + Spring Boot.
- Electron is the desktop shell and process orchestrator.
- Spring Boot is the local backend/API and database owner.
- SQLite remains the local database.
- Flyway owns database migrations.
- Maven owns backend builds.
- React + JavaScript remains the frontend.
- Frontend source uses JavaScript/JSX, not TypeScript.
- Electron renderer never accesses SQLite or backend internals directly.

## 33. Guiding principle

This is farm software, not a spreadsheet wearing an Electron costume.

Record facts.

Preserve history.

Calculate state.

Explain every important number.

Never double-count a cost.

Never lose confirmed data.

Never hide corrections.

And never sacrifice poultry math for dashboard cosmetics.

## Stage 9: Daily farm operations

- Daily records are batch-scoped and use farm-local business dates.
- A daily record stores notes and water-container entries. Bird count, mortality, culling, and feed are derived from authoritative records and are not duplicated into the daily-record table.
- Mortality and culling remain separate population event types. Both support reason and notes.
- Water is recorded as configured container size × count. Do not ask workers for invented precise volume measurements.
- Water total is calculated as the sum of configured container capacity × container count.
- Feed usage continues to be recorded through the feed module and is aggregated into daily records rather than creating a second feed ledger.
- One daily-record header is allowed per batch/date.
