# AGENTS.md — Poultry Management Desktop

Instructions for AI coding agents and humans working in this repository. Read this file and `docs/` before changing anything. If code, conventions, commands, or domain rules change, update this file and the relevant documentation in the same change.

## 1. Product

A **local-first poultry farm management system for layer, broiler, and turkey farms**, delivered as a Windows Electron desktop app and usable fully offline.

Core loop:

**record what happened → calculate what is happening → show what needs attention → produce reports.**

Priority order:

1. Correct records and calculations
2. Never lose data
3. Fast, simple data entry for farm workers
4. Clear batch-level and farm-level visibility
5. Dashboard polish

A boring screen with correct poultry numbers beats a beautiful dashboard showing agricultural fiction.

**Current stage: 17 — Independent Turkey support.**

Stage 8 feed management is implemented. Stage 9 daily farm operations is implemented. Stage 10 health management is implemented. Stage 11 added Layer-only egg production, egg inventory, cracked-egg history, configurable crate conversion, and egg sales. Stage 12 adds Broiler-only weight records, growth calculations, backend-owned FCR, bird sales, and automatic SOLD lifecycle locking. Stage 13 adds cost-based Broiler pricing, configurable target margin, working/latest margin, and audited below-target sale confirmation. Stage 14 adds a simple farm/batch expense ledger. Stage 15 adds reusable customers and a common sales ledger while preserving Layer egg and Broiler bird sale behavior. Stage 16 adds a transaction-based inventory system for drugs, vaccines, and farm supplies, while the existing feed subsystem remains authoritative for feed stock and FIFO costing. Feed definitions may include brand, package name, and confirmed package weight in kilograms; unknown weights must remain unset. Feed product identity is the normalized feed name plus normalized brand within a farm, so the same name under different brands is allowed while duplicate name/brand pairs are rejected. Identity edits update the existing feed type ID and must not rewrite purchases, opening stock, usage, expenses, FIFO history, unit, or applicability. Historical feed CSV rows must include feed_type_brand when the name is ambiguous. Feed quantities continue to use the existing precision-safe FIFO ledger; do not infer package weights or applicability from product names.

Do not build later-stage features unless the owner explicitly changes the stage.

### Branding

The product is branded **Pomas** (egg-and-M logo). The root `package.json` version is the authoritative desktop application release version. Electron exposes `app.getVersion()` and the actual `process.arch` through the narrow preload IPC bridge; architecture is runtime metadata only and must never be rendered as product/version text. Do not hardcode release versions in UI components. Windows packages explicitly target x64 and use `POMAS-v<version>-Setup-x64.exe`. Keep the backend Maven artifact version independent from the desktop release version. Packaged Windows metadata uses the existing `Grantino Farms` product identity. Logo sources: `build/icon.ico` and `build/icon.png` (installer/executable), `app/assets/icon.png` (window icon), `app/renderer/src/assets/pomas-logo.png` (sidebar mark).

The installer ID (`appId`), repository name, Java packages, and database file name are intentionally unchanged. Packaged builds pin Electron's `userData` to the folder `%APPDATA%/Poultry Farm Manager` (see `app/main/main.js`) so installs created before the rename keep finding their data. Do not remove that pin without a data-migration plan.


## 2. Non-negotiables

1. Derived state is calculated, never manually stored or maintained.
2. Each formula has exactly one implementation in the shared domain/calculation layer and is tested.
3. Money is integer minor units. Never use floating-point money. In the renderer, format every user-visible monetary amount with `app/renderer/src/utils/money.js`; never display raw minor-unit fields. See `docs/money-display.md`.
4. Transactions are voided, not deleted. Important changes are audited.
5. The Electron renderer has no Node.js, filesystem, or database access.
6. Electron IPC validates every input.
7. The Spring Boot backend validates every request at the API boundary.
8. The Electron app communicates with Spring Boot through a REST API. Do not introduce a second backend API style.
9. Backups use SQLite `backup` functionality or `VACUUM INTO`, never a raw copy of a live DB.
10. The application must remain fully usable without internet access.
11. No telemetry, cloud dependency, CDN assets, or third-party network service in normal operation.
12. Never edit a shipped migration. Add a new migration.
13. Keep the saved start-page allow-list aligned across Settings UI, backend request validation, SQLite CHECK constraints, and start-page fallback routing: `dashboard`, `flocks`, `daily-work`, `feed-stock`, `health`, `production`, `sales-money`, `reports`, `settings`. Import / Export is a routable top-level destination but is intentionally not selectable as the saved start page.
14. Never weaken/delete a test to make it pass.
15. Do not introduce TypeScript into the frontend. The frontend is React + JavaScript.
16. Never commit `*.db`, `*.db-wal`, `*.db-shm`, backups, or real farm data.
17. Keep one local farm data model. Do not unnecessarily split the farm into separate databases/accounts.
18. Stage 1 has one primary owner/operator account. Multi-user roles can be added later.
19. Opening a new batch creates a new permanent batch record. It does not overwrite an old batch.
20. The broiler terminal status is **SOLD**, not COMPLETED.
21. Reopening a SOLD batch is an exceptional, audited action and requires a reason.
22. Posted farm transactions are corrected by an audited reversal plus a separate replacement, never by editing or deleting the original. Corrections must be atomic, reasoned, actor-attributed, and validated against later dependent records. Preserve FIFO feed costing, bird-cost allocation, bird populations, egg balances, common sales rows, and generated expenses. See `docs/decisions/2026-10-26-auditable-transaction-corrections.md`.

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
- React is responsible for presentation, interaction, and display-level analytics derivations from raw backend aggregates. Spring Boot remains authoritative for transactional domain rules, validation, persistence, audit, and persisted business calculations. Renderer-derived reporting values are presentation-only and must never be stored or used to bypass backend validation.
- Keep the backend capable of running locally on the same PC as the Electron application.
- Prefer a packaged/local Spring Boot process launched and supervised by Electron for the desktop distribution.
- The jlink runtime shipped with the desktop app must include `java.desktop` and the backend's required modules; `package:verify` checks the generated runtime before packaging.
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
- navigation, using the dependency-free hash routes documented in `docs/frontend-navigation.md`
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

## House classification and occupancy

Every house has an explicit `houseType` of `PERMANENT` or `TEMPORARY`. Never derive house type from a code prefix. V28 uses `PERMANENT` as a deliberate legacy-row default so existing installations migrate safely; administrators must reclassify any legacy temporary houses after upgrade. New house creation requires an explicit type. House occupancy is derived from the existence of at least one `ACTIVE` batch assigned to that house; SOLD/CLOSED/ARCHIVED batches do not occupy it. Houses can be reused after their prior batch is no longer active. Farm dashboard fields are `totalHouses`, `permanentHouses`, `temporaryHouses`, `occupiedHouses`, and `emptyHouses`, counting ACTIVE houses only. See `docs/database-schema.md`.

## Estimated and verified bird population

Every batch has a population_count_status of ESTIMATED or VERIFIED. Default new and migrated batch counts to ESTIMATED; never imply a physical count was performed when it was not. Initial population is immutable and remains the starting baseline. Calculated population is initial birds plus/minus dated mortality, culling, sales, transfers, and signed physical-count adjustment events. An adjustment is not mortality, culling, or a sale and must never fabricate those records.

Physical count adjustments are append-only records containing the previous calculated count, physically counted quantity, signed difference, effective date, required reason, authenticated owner user ID, and server-generated UTC timestamp. Validate the entire later population history before accepting a backdated adjustment; reject it if any later event would make population negative. A successful physical count adjustment marks the batch VERIFIED. Administrators can explicitly mark a batch ESTIMATED again with a required reason and audit event. Population adjustments change live quantity without creating a new purchase cost or a mortality/culling cost allocation. Existing carried bird cost remains with the batch and its per-bird average is recalculated against the reconciled population. Farm dashboards and reports must expose estimated batch/bird totals so provisional populations are not presented as verified facts. Transfers remain paired source/target events and must not change the farm-wide total. See docs/decisions/2026-11-06-estimated-population-adjustments.md and docs/database-schema.md.

## Turkey reporting contract

Turkey is an independent `TURKEY` batch type, not a Broiler subtype. Farm dashboard totals must expose `turkeyBirds` separately from `broilerBirds` and `layerBirds`. Farm reports must keep `broilerGrowth` BROILER-only and expose `turkeyGrowth` for TURKEY-only results; `meatBirdGrowth` is the explicitly combined BROILER + TURKEY view. Turkey uses the shared meat-bird weight/growth and bird-sale services, but egg collection remains LAYER-only and must be rejected by backend validation. See `docs/decisions/2026-11-06-independent-turkey-reporting.md`.

## Historical production CSV import

The Import / Export screen supports versioned CSV imports for layer egg collections (`egg-collections`) and broiler weight records (`broiler-weights`) for existing ACTIVE batches. Batch references use human-readable batch codes. Egg imports must use LAYER batches and preserve good/cracked separation; weight imports must use BROILER batches and store sample count plus total sample weight in kilograms. All writes go through `EggManagementService` or `BroilerProductionService` in a single transaction so existing validation and audit behavior remain authoritative. Do not import derived averages, growth, sellable cracked eggs, or synthetic zero records for missing dates. See `docs/csv-bulk-import.md`.

## 5. Electron security

Use:

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true` where compatible with the application
- narrow typed `contextBridge`
- no direct `ipcRenderer` exposure
- no direct `fs` exposure
- strict CSP: packaged renderer allows bundled same-origin resources and the local API only on `127.0.0.1` using a dynamic port; development-only Vite HMR sources are injected only in serve mode and must never ship
- controlled navigation
- controlled window creation

The renderer communicates with the backend through the application's API client.

Spring Boot should bind only to localhost for the local desktop deployment.

Do not expose the local API to the LAN unless the owner explicitly changes the product security model.

## 6. Backend lifecycle

Electron is the desktop orchestrator.

Development:

- start Spring Boot locally
- start Electron/React locally, or use the documented browser-only Vite workflow (`npm run dev:browser:all`)
- Electron selects an available loopback port for the packaged backend, passes a per-launch instance ID to Spring Boot, and accepts readiness only when the health response echoes that ID
- Electron exposes the selected backend URL through the validated `backend:base-url` IPC handler; the desktop renderer must use that IPC instead of a hardcoded port
- the datasource URL must be derived from `poultry.database-path`; never hardcode a developer-specific database path
- Spring Boot file logs go to `logs/backend.log`; Electron-captured process stdout/stderr and startup diagnostics go to `logs/backend-process.log`

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
- type: LAYER, BROILER, or TURKEY
- placement/opening date
- initial bird count
- house/pen
- supplier/source where applicable
- original bird purchase cost in integer minor units when recorded; otherwise explicitly unknown
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

Broilers and turkeys additionally:
- Weight
- Growth
- FCR
- Bird Sales
- SOLD lifecycle

Stage 12 Broiler rules:
- Weight records are Broiler-only and store sample quantity plus total sample weight in kilograms.
- Average weight is calculated as total sample weight divided by sampled birds.
- Growth is calculated from ordered weight records; the first recorded average weight is the baseline for the batch growth series.
- FCR is calculated only by Spring Boot: feed consumed in kilograms divided by live-weight gain in kilograms. Frontend code must display the backend result and never calculate FCR.
- FCR uses feed consumed in kilograms divided by live-weight gain in kilograms. Each feed type has a nullable `kg_per_unit`; kg uses 1, while non-kg units require an explicit positive conversion. If any feed usage in the batch has an unknown conversion, converted feed kilograms and FCR are undefined (`null`, displayed as —). Never guess a bag size. Conversion changes are audited and affect interpretation of historical feed usage for FCR, while original feed quantities and FIFO costs remain unchanged.
- Bird sales are recorded separately from mortality/culling and create SOLD population events.
- A Broiler becomes SOLD automatically when a bird sale leaves zero live birds as of the current farm date. SOLD batches reject ordinary production/population writes.
- Weight and sales records cannot be dated before batch placement.

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

When actual purchase information is available, record the original bird purchase cost in integer minor units and preserve it in both the batch opening data and `bird_purchases` history. For existing stock with an unknown/unrecorded cost, keep `batches.original_purchase_cost_minor` NULL and create no `bird_purchases` row. NULL means unknown; zero means a genuine recorded zero-cost purchase. Never substitute zero for an unknown cost. Cost-derived values and margins remain unavailable when the initial cost is unknown. Population records remain available, but transferring birds from an unknown-cost batch is rejected because the carried cost cannot be determined.

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

## 16. Expense system

The initial expense system is deliberately simple.

Each expense records:
- business date
- description
- amount in integer minor currency units
- category
- optional batch association

An expense with no batch association is a farm-level expense.

The initial categories are:
- `FEED`
- `DRUGS`
- `OTHER`

Drug records continue to create their own shared `DRUGS` expense automatically. Feed purchases continue to create their own `FEED` expense automatically.

Do not add a large category taxonomy yet. Electricity, transport, equipment, repairs, and similar costs remain represented by `OTHER` until the owner explicitly expands the category model.

Expenses are historical financial records. Do not hard-delete them.

## 17. Customers and sales

Customers are farm-scoped reference records with:
- name
- optional phone
- optional notes

Customers are retained because sales are historical records. Do not hard-delete a customer that may be referenced by a sale.

The common `sales` ledger is the commercial history shared by Layer and Broiler sales. It records:
- sale date
- customer
- batch
- sale type
- quantity
- unit
- unit price in integer minor units
- total amount in integer minor units
- reference to the type-specific sale record

The type-specific sale tables remain authoritative for production behavior:
- Layer sales remain egg sales using configured crate conversion and good-egg inventory validation.
- Broiler sales remain bird sales using population reduction, cost-based pricing, below-target confirmation, and SOLD lifecycle rules.

The common ledger must be written in the same transaction as its type-specific sale. It is a shared commercial view of the sale, not a second source of truth for production calculations.

For common sales:
- Layer egg sales use `CRATE` as the commercial unit and preserve the exact crate quantity.
- Broiler bird sales use `BIRD` as the commercial unit and use integer bird quantity.

Existing egg and bird sales are backfilled into customers and the common sales ledger by the Stage 15 migration.

## 18. Inventory management

Inventory is transaction-based. Never store a mutable stock balance.

Stage 16 tracks:
- drugs
- vaccines
- farm supplies

Each inventory item has a farm-scoped name, category, unit, reorder level, and active/archived status.

Each inventory movement records:
- business date
- positive quantity
- movement type
- reason
- source
- optional batch association

Supported movement types:
- RECEIVE
- ISSUE
- ADJUST_IN
- ADJUST_OUT
- WASTE

Stock is calculated as receipts and positive adjustments minus issues, negative adjustments, and waste. Stock cannot become negative.

Feed remains under the existing feed subsystem because feed inventory is coupled to FIFO purchase lots, feed consumption, and bird-cost allocation. Feed usage now also stores a movement reason; older feed rows may have a null reason for historical compatibility.

Inventory records are not hard-deleted. Items are archived. Movements are append-only.

## 19. Suppliers and bird purchase history

Supplier records are important for tracing purchases.

For broilers, preserve:
- supplier/source
- purchase date
- purchase quantity
- original cost per bird
- original total purchase cost

Historical purchase price must remain attached to the original purchase/batch data.

Do not silently replace it with today's price.

## 19. Broiler pricing and target margin

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

## 20. Money

Money is integer minor units. Never use JS or Java floating-point types for money.

Use integer minor units for persistence and API DTOs. In Java, prefer `long` for monetary minor units.

Revenue:

`quantity × unit_price`

Profit:

`revenue - attributable costs - expenses`

Round only at the appropriate line-total boundary.

Profit must account for the attributable cost of sold birds/eggs and relevant expenses.

## 21. Core calculations

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

## 22. API design

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

## 23. Authentication and user model

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
- the renderer must check session/setup status before calling protected APIs and provide first-run owner setup and sign-in flows
- first-run owner setup is shown only when `setupRequired` is true; normal sign-in is a separate state and must not show a sign-up link or unsupported account-switch action
- login JSON contract is `loginIdentifier` (username or email), `password`, and optional `rememberMe`; keep the React API client aligned with Spring Boot `LoginRequest`
- read submitted named form controls on auth submit so browser autofill works even when React change events are skipped; reject blank credentials in the renderer and retain backend request validation
- keep the request contract and state-driven UX documented in `docs/authentication-flow.md`

Multi-user roles and permissions are future work.

Authentication performance rules:
- Never reduce password-hashing work or cache password-verification results to improve timings.
- Keep setup/login stage timings free of passwords, session tokens, and other secrets.
- Public setup/login endpoints must not perform unrelated session lookups or bootstrap user-count queries in the auth filter; keep required session validation on protected routes and `/auth/me`.
- Use `scripts/benchmark-auth.ps1` and a fresh disposable SQLite database for repeatable timing runs. Keep cold-start and warm-request results separate; never claim a measured improvement without multiple before/after runs.


## 24. Audit trail

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

## 25. Database rules

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

## 26. Migrations

Use **Flyway** for schema migrations.

Migrations are numbered, forward-only, and never edited after shipment.

A backup is created before migration.

Every schema migration updates `docs/database-schema.md`.

Tests apply all migrations to a clean database.

Do not mix Flyway migration ownership with ad-hoc schema creation in application startup.

## 27. Backup and restore

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

## 28. UI rules

The app is designed for farm workers.

Prioritize:
- keyboard-first entry
- logical Tab order
- Enter to save where appropriate
- today's date defaults
- last-used batch defaults
- visible units and persistent field labels that remain visible after typing
- money entry in the farm currency (for NGN, enter naira with up to two decimal places); convert string input to integer kobo without floating-point arithmetic before API submission
- readable table text and headers, with navigation and keyboard shortcuts comfortably legible at 1366×768
- plain-language errors
- confirmation before voiding
- 1366×768 usability

Abbreviations such as HDP and FCR must have formula tooltips.

All null metrics display **—**.

The UI presents data returned by the backend. It does not own authoritative farm calculations.

### Accessibility rules

- Essential normal text must meet WCAG AA contrast of at least 4.5:1 against its rendered background. Do not use the large-text exception for small labels, table headings, status text, or shortcut hints.
- Keep persistent form labels and primary navigation at 12 px or larger; navigation labels should target 13 px. Table headers, metric labels, and keyboard shortcut hints should be at least 12 px.
- Supporting metadata may remain 11 px only when it is genuinely secondary and still meets contrast requirements. Do not make primary information rely on decorative kickers, color alone, or tiny text.
- Check both Light and Dark themes. Run `node scripts/verify-a11y.mjs` as part of the production build, then manually review the rendered app at 1366×768 and 1100×700. Automated token checks do not replace rendered keyboard/visual review.
## Accessibility and keyboard navigation

- Place a skip-to-main-content link before primary navigation in DOM order.
- Keep the skip link out of the normal layout, but make it clearly visible with a high-contrast focus treatment when keyboard-focused.
- The skip link must move focus to a focusable main landmark without breaking the hash-based route.
- Manually test keyboard-only navigation on Dashboard and at least one other main screen.

## 29. Navigation

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

## 30. Testing

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

## 31. Delivery plan

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


### Stage 15 — Sales and customers

Build:
- reusable farm-scoped customers with name, phone, and notes
- common sales ledger
- customer references on Layer and Broiler sales
- common sales history/filtering
- migration of existing sale customer names into customer records

Done when every new Layer or Broiler sale preserves its existing type-specific rules while also appearing exactly once in the common sales ledger with date, customer, batch, quantity, unit, unit price, and total.

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

## 32. Working agreement

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

## 33. Owner decisions already made

These decisions are intentional and should not be changed casually:

- One primary owner/operator for now.
- Batch-level dashboards plus one farm-wide dashboard.
- Farm-wide dashboard is the first/front page.
- Batch reports contain only that batch; the main report combines the farm.
- Broiler terminal lifecycle state is SOLD when all birds are sold.
- Reopening requires an audited reason.
- Feed purchases are inventory and expense at purchase time; later batch consumption is allocation, not a second expense.
- Feed types are configurable and declare a unit plus a non-empty set of applicable bird types.
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

## 34. Guiding principle

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


## 21. Display theme

- The owner-approved Pomas brand palette is deep teal `#1A4D5C` and golden yellow `#F4B41A`. This decision is dated 2026-10-27 and supersedes earlier theme decisions.
- Light mode uses white surfaces. Deep teal is used for primary actions, headings, and accent text. Gold is reserved for fills, indicators, and focus accents; never use gold text on white.
- Dark mode uses black `#000000` and near-black `#0A0A0A` surfaces, off-white body text, and gold accents. Teal is limited to fills and borders that do not carry text; never use teal text on black.
- Primary buttons use white text on teal in light mode and black text on gold in dark mode.
- Warning, critical, and error colours must remain semantically distinct from brand colours.
- Dashboard attention cards are content surfaces, not primary actions: exclude `.attention-item` from global primary-button styling, use neutral semantic panel/text tokens, and keep warning, critical, and error severity badges on distinct contrast-safe token pairs.
- All colours must be assigned through semantic CSS tokens. Do not add per-screen hardcoded colour values.
- Landing, sign-in, and first-run owner-setup screens must use the shared --auth-* semantic tokens for both themes. Keep their theme selector available before authentication; do not introduce fixed page colours in screen selectors.
- Keep the external theme bootstrap in `app/renderer/public/theme-init.js` referenced from the document head before the renderer module. It must remain compatible with the packaged renderer CSP and must not become an inline script.
- Preserve Auto, Light, and Dark behavior. Auto follows the operating system colour scheme and updates when the system preference changes.
- Update `docs/accessibility.md` and `scripts/verify-a11y.mjs` whenever theme tokens or required contrast pairs change. Keep all existing tests; never weaken or delete tests to obtain a pass.
- The automated checker validates token pairs only. Do not claim full rendered contrast or runtime layout validation without testing the running app at 1366×768 and 1100×650 in Auto, Light, and Dark.

## Local backend reliability

- Treat Spring Boot + SQLite as the source of truth for farm records; browser localStorage is not the persistence layer.
- Bound renderer API and health-check requests with timeouts. A failed request must never be represented as a successful backend connection.
- Keep an actionable unavailable/recovery state visible until a retry successfully reloads backend data, even if the health check begins responding again.
- When retrying data after an outage, preserve the mounted screen and controlled form input wherever practical.
- Test outage and restart behavior only against an explicitly disposable SQLite database. Never delete, reset, truncate, or replace a real farm database during reliability testing.
- Verify committed records after a backend restart using the same disposable database path. Document runtime tests separately from automated checks; never claim the restart drill passed unless it was actually run.

## Frontend page architecture

- Keep the renderer organized around ten top-level destinations: Dashboard, Flocks, Daily Work, Feed & Stock, Health, Production, Sales & Money, Reports, Import / Export, and Settings. Import / Export is routable through the local hash router but is intentionally excluded from the saved start-page allow-list.
- Use the local hash router in `AppShell.jsx`; do not add a routing dependency without explicit owner approval.
- Navigation must not depend on scroll position, scroll listeners, or `scrollIntoView`.
- Wrap page content in `PageSection` so inactive pages are not mounted. Keep domain calculations and persistence in the existing backend/API layers.
- Add or update renderer tests for route changes and inactive-page rendering. Keep all source in JavaScript/JSX.

## 35. Renderer font assets and network requests

- The renderer uses the system UI stack documented in `docs/font-audit.md`; do not assume Inter is installed or bundle a font without a documented UI need.
- Do not add remote font services, CDNs, or external stylesheet imports. The app must remain usable offline.
- Do not remove KaTeX or any other font/package based solely on a browser report. Verify tracked assets, dependency manifests, CSS declarations/imports, and request initiators first.
- If a web font is deliberately approved, document its exact UI use, local/offline packaging behavior, license, asset size, and use `font-display: swap` where applicable.
- Run `npm run build`; the build must run `scripts/audit-font-assets.mjs` against both renderer source and built output.
- Development and packaged renderer Network/Console checks are separate manual validation. Do not claim the packaged renderer has no missing-font errors until the packaged app has actually been launched and inspected.
- Record before/after font request counts and transferred bytes when a runtime report is supplied. Distinguish repository asset inventory from actual browser transfer measurements.


## 36. Responsive layout and supported window size

- Pomas is a desktop-only Windows app. Keep the renderer minimum canvas width aligned with Electron's `minWidth: 1100` and `minHeight: 650`.
- Do not imply phone/tablet support through narrow-screen navigation transformations. Browser emulation below 1100px is diagnostic only; document it as unsupported, not as a passed responsive layout.
- At supported dimensions, keep primary navigation reachable, prevent page-level horizontal overflow, and contain wide data tables in their own scroll region. Forms and action buttons must remain operable.
- Before introducing a hamburger menu or other navigation redesign, reproduce and record the failure at a supported window size. Follow `docs/responsive-layout-audit.md` and distinguish static source inspection from actual browser/Electron runtime verification.
- Resize and test the packaged Electron app at its 1100×650 minimum and at the 1366×768 default. Do not claim runtime validation unless those checks were actually performed.


## Connection status presentation

Show local Spring Boot API connection state in exactly one primary UI location: the top-bar status pill. Use a clear connecting label before the first health check completes, then connected or unavailable. When connectivity is lost or returns but data has not yet been refreshed, retain the prominent recovery alert and Retry connection action because it communicates the required recovery step, not a duplicate passive status. Do not reintroduce the same connection state in the sidebar or bottom status bar. Keep the app shell usable without a bottom status bar.

Validate the renderer with the backend available, during startup, and with the backend stopped. Confirm the top-bar state updates and the outage alert remains understandable and retryable. These runtime checks must be recorded as not run unless actually performed.


## 37. Audit activity presentation

- Audit records are immutable source data. Humanize values in the renderer only; never rewrite stored action, entity, reason, or timestamp values to improve display.
- Render audit timestamps in the farm's configured IANA timezone (for example, `Africa/Lagos`). Use a consistent day-month-year and 24-hour time format, including seconds. ISO fractional seconds beyond milliseconds may be truncated only for display parsing.
- Handle invalid/missing timestamps and missing/unknown action/entity values with readable fallbacks. Unknown non-empty values should be humanized rather than hidden.
- Keep the dashboard audit table inside its existing scroll container so long values do not force page-level horizontal overflow at the supported 1100×650 and 1366×768 window sizes.
- Cover timestamp formatting, nanosecond ISO values, timezone fallback, known action/entity labels, and unknown/missing values with Vitest. Runtime table-width verification is a separate manual check.


## 38. First-run farm dashboard empty state

- Show first-run guidance only when the farm has no houses or batches, core calculated metrics and feed type count are zero, and there are no audit records. Do not classify a farm as empty solely because one metric is zero.
- Explain the sequence: create a house/pen, create a batch, set up and record feed, then enter daily records. Actions must navigate to the existing local hash routes.
- The empty state is presentation-only. Never seed, synthesize, or write demo records to the database.
- Keep the normal dashboard metrics, feed, attention, and audit layout for populated farms or farms with meaningful recorded activity.
- Cover the empty and populated states with renderer tests. Validate with a clean disposable database and a populated test farm; never reset or alter a user's real database.


- Keep setup guidance data-driven after the first house is created. The dashboard checklist must derive completion from saved house, batch, feed-type, and inventory-item records, and remain visible until complete or dismissed.
- Keep the checklist dismissal local to the current computer. Do not treat page visits as completed setup tasks.
- Every top-level page must provide concise contextual help and a next-action link. Keep those links aligned with the documented hash routes in docs/frontend-navigation.md.
- Cover empty, partially configured, completed, and dismissed checklist states in renderer tests; test page-help navigation as well.

## Stock alert rules

- An active feed type with no purchase history and no usage history is configured but not yet stocked; do not emit a low-feed-stock alert for it.
- Once a feed type has purchase or usage history, a depleted balance is a critical alert. When stock remains, warn only when estimated cover is at or below three days.
- Inventory reorder level `0` disables low-stock alerts for that item. Positive reorder levels are active thresholds: stock at or below the threshold alerts, with zero stock critical and positive stock warning.
- Archived inventory items are excluded from alerts. Never rewrite existing reorder levels to suppress an alert.
- Farm dashboard inventory-alert counts must use the same positive-threshold rule as the attention list.

## 39. Report exports and date scope

- Farm dashboard and farm/batch reports share one inclusive date range with Today, Yesterday, Last 7 days, Last 30 days, Last 90 days, This month, Last month, This year, and Custom range presets.
- Preset boundaries use the farm's IANA timezone, not the computer's timezone. Date-only values remain calendar dates through the API. Audit timestamps are filtered using farm-local day boundaries converted to instants.
- Custom ranges require both dates and start date <= end date. Reject invalid ranges before requesting data and show clear feedback.
- Compatible activity metrics, records, report previews, exports, audit activity, and production charts use the same selected from/to range. Range changes must never write or mutate farm records.
- Current/lifetime snapshots such as current live population, house occupancy, feed stock, egg inventory, and batch-to-date values remain snapshots. Do not silently filter them as period activity; label the distinction in the UI.
- Backend period responses expose raw period totals and range-filtered source rows. React may derive presentation-only period metrics, including profit from integer minor-unit revenue minus expenses. Do not persist these derived display values.
- CSV exports use stable documented columns with report type, period start, period end, snapshot date, section, record, field, value, and unit. PDF exports include the report title and selected period, readable column headings, row separation, and repeated table headers when a report spans pages.
- Export formatting may serialize renderer-derived display metrics supplied by the request, but must not independently recalculate farm metrics.
- Production trend endpoints retain their 366-day maximum. A longer custom range may still filter other report panels; the chart must show a clear range-limit state.
- Test month/year boundaries, leap day, custom and invalid ranges, farm-local dates, inclusive boundaries, range filtering, current-snapshot preservation, export metadata, and backend reconciliation.

## 40. Production trend charts

- Layer production trend is backend-owned HDP% = total eggs collected on the business date (good + cracked) ÷ live layer birds on that date × 100. Use the historical bird-population calculation for each date; do not calculate the denominator in React.
- Broiler daily average weight is total sampled weight in kilograms ÷ total sampled birds for all weight records on that date. Aggregate grams and sample counts before dividing; do not average record averages.
- Layer HDP and broiler average-weight endpoints accept only their matching batch type. They must reject incompatible batch types and dates before placement.
- Trend ranges are inclusive and limited to 366 days. If no start date is supplied, use the most recent 30 days but never start before batch placement.
- Return a point for every calendar day in the selected period. A date with no production record has `value: null` and `recorded: false`; never turn a missing record into zero. An explicitly recorded zero-egg collection is a real 0% value.
- Use kilograms for broiler average weight and percent for HDP. Backend responses include metric/unit and the missing-day policy.
- Charts must display loading, empty, and error states; accessible names; dates and values in tooltips; and visible gaps for missing days. They must refresh after relevant successful production writes.


## Farm water unit label

- The farm's water unit label is a display label, defaulting to `litres`. Changing it must never convert or rewrite stored container capacities, container counts, or historical water quantities.
- Read the label from farm settings for water-entry forms and farm/batch report views and exports. Do not hard-code `units` or `litres` in a water-specific display.
- Water unit setting changes must be audited with both the prior and updated settings snapshots so the change is traceable.
- Keep the migration forward-only. Migration tests must start from a pre-label database containing existing water records and assert the label default plus unchanged numeric capacity/count values.


## JSX text and line breaks

- Never paste the two-character escape sequence `\\n` between JSX elements to represent visual spacing or a line break. JSX renders it as literal text. Put each element on its own JSX line and use semantic grouping, CSS `gap`, or an explicit layout wrapper for spacing.
- When reviewing escaped text, distinguish source-code formatting from user-entered values. Fix only unintended hard-coded UI escape sequences; never globally strip or normalize backslashes in user-provided names, notes, reasons, or other stored text.
- Add a renderer regression test for any reported literal escape sequence so the affected screen is checked as rendered DOM text.


## Multi-type feed applicability

- Feed applicability is a non-empty set of `LAYER`, `BROILER`, and `TURKEY`; every non-empty combination is supported.
- Persist the set in `feed_type_applicability`. The legacy `feed_types.applicable_type` field is compatibility metadata only and must not drive business rules.
- V32 maps legacy `BOTH` to all three supported types and maps each legacy single type to its matching one. Never infer applicability from feed/product names.
- Validate applicability in FeedService, historical feed CSV preview/import, and batch feed usage. The renderer shows independent checkboxes and filters Daily Work options to active feeds compatible with the selected batch.
- Do not modify feed purchases, feed opening balances, feed usage, FIFO allocation, or expenses while migrating applicability. Add backend coverage for every one-, two-, and three-type combination and a migration test that preserves feed history.

## Opening feed stock and FIFO valuation

Opening feed stock is stored in the existing `feed_opening_balances` table. Do not create a duplicate feed ledger or a purchase, supplier, invoice, or expense for opening stock. Opening balances enter the same FIFO sequence as purchased lots, ordered by effective date and then creation timestamp. Opening lots are unvalued: never assign zero or estimated cost. If a usage consumes any unvalued opening quantity, its total feed cost is unknown (`null`). Preserve and allocate the known cost portion consumed from purchased lots to the batch bird-cost pool, exposing it as `knownFeedCostMinor`; never invent cost for the unvalued portion. A usage consuming only opening stock has no known purchased-feed cost and creates no bird additional-cost event. Remaining or consumed inventory cost, batch feed cost, and per-bird feed cost must remain unknown whenever their included quantities contain unvalued stock. Render unknown as “Unknown”, never as currency zero.

An opening balance may be dated before a later purchase, but cannot be dated before the latest existing usage or latest opening-balance entry for the same feed type. This prevents retroactive changes to FIFO allocations. Preserve the existing purchase rule that an actual purchase creates exactly one feed expense. See `docs/decisions/2026-10-02-feed-management.md`.
