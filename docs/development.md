# Development

## Prerequisites

- Windows for the primary desktop workflow
- Node.js 20+
- npm
- JDK 17
- Git
- PowerShell for the documented Windows commands

The Maven Wrapper is included, so a separate Maven installation is not required.

Packaging additionally requires a valid `JAVA_HOME` containing `jlink`. The trimmed
runtime includes `java.desktop` (required by Spring Boot property binding) and the
HTTP, security, database, and other runtime modules needed by the packaged backend.
`npm run package:verify` checks that all required build outputs, the Spring Boot JAR, and generated runtime are present.

## Setup

```powershell
git clone https://github.com/ENJ0Y-Labs/poultry-desktop.git
cd poultry-desktop
npm install
```

Verify Java:

```powershell
java -version
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\jlink.exe" --version
```

## Run the desktop application

Start Electron, React, and the supervised Spring Boot backend:

```powershell
npm run dev
```

On first launch, create the owner account. On later launches, sign in before the renderer loads farm or account data.

## Run the UI in a browser

For browser-only development, run both services together:

```powershell
npm run dev:browser:all
```

Open `http://127.0.0.1:5173` in Chrome or another browser. Vite provides frontend hot reload; Spring Boot serves the API on `http://127.0.0.1:18942/api/v1`.

You can also start the services separately in two terminals:

```powershell
npm run dev:backend
```

```powershell
npm run dev:browser
```

The browser workflow uses the same local development database as Electron: `data/poultry.db`. The database path is supplied through `poultry.database-path`; the datasource URL must resolve from that property rather than a machine-specific absolute path. Spring Boot logs are written to `logs/backend.log`; Electron process output and startup diagnostics are written separately to `logs/backend-process.log`.

The browser does not provide Electron-only native features such as choosing a backup folder through the desktop dialog. Test those features with `npm run dev`.

## Commands

Frontend tests:

```powershell
npm test
```

Frontend watch mode:

```powershell
npm run test:watch
```

Lint:

```powershell
npm run lint
```

Frontend/Electron build:

```powershell
npm run build
```

Backend tests:

```powershell
npm run backend:test
```

Backend verification:

```powershell
npm run backend:verify
```

Backend package:

```powershell
npm run backend:package
```

Production package:

```powershell
npm run package
```

## Backend-specific commands

From the repository root:

```powershell
.mvnw.cmd -f backend\pom.xml test
.mvnw.cmd -f backend\pom.xml verify
.mvnw.cmd -f backend\pom.xml package
```

Run a focused test:

```powershell
.mvnw.cmd -f backend\pom.xml -Dtest=SettingsManagementIntegrationTest test
```

## Test strategy

Frontend tests use Vitest and Testing Library.

Backend tests include:

- unit tests for calculation logic
- repository/database integration tests
- Flyway/schema tests
- service integration tests
- controller/error tests
- security/password hashing tests
- backup/restore-related tests
- performance baseline tests
- settings integration tests

The backend integration tests use SQLite rather than mocking away the database behavior that matters to production.


## Authentication timing investigation

Run authentication timing only against a fresh, disposable SQLite database. Do not use the normal development database or a real farm database. The benchmark creates an owner account and must not be pointed at an existing installation.

In Terminal 1, create a uniquely named database path and start the backend with it:

```powershell
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
New-Item -ItemType Directory -Force "data\auth-benchmark-$stamp" | Out-Null
$env:DATABASE_PATH = Join-Path (Get-Location) "data\auth-benchmark-$stamp\pomas-auth-benchmark.db"
$env:LOG_FILE = Join-Path (Get-Location) "logs\auth-benchmark-$stamp.log"
npm run dev:backend
```

Wait until Spring Boot reports that it has started. In Terminal 2, from the repository root, run:

```powershell
.\scripts\benchmark-auth.ps1
```

The script records the first successful setup request, three rejected duplicate-setup requests, and five successful login requests to a timestamped CSV under `logs\`. It records HTTP status and elapsed request time without recording passwords or session tokens. The backend emits `auth_setup_timing` and `auth_login_timing` entries that break successful/invalid authentication work into database lookup, password hashing/verification, session cleanup, session write, and total service time.

For each run, preserve:
- whether the backend was a clean launch or already warm
- backend startup duration (start the stopwatch immediately before `npm run dev:backend`, stop when the ready message appears)
- the generated CSV and matching backend log
- machine/runtime details and the Git commit
- medians for repeated login requests, while keeping the first request separate

Repeat the full procedure at least three times using a new disposable database path each time. Compare cold-start and warm-request timings separately. A single sample is not a performance conclusion. Do not cache passwords, password-verification results, setup state across writes, or session authentication results. Keep PBKDF2 parameters unchanged unless a separate security review explicitly approves a migration plan.


## Local backend outage and persistence drill

Run this drill only against a disposable SQLite database. Never point it at the normal development database or a real farm database. The drill must not delete, reset, replace, or truncate a database.

Open two PowerShell terminals at the repository root. Confirm port 18942 is not being used by another backend before starting.

In Terminal 1, create a dedicated test-data directory and start the browser UI first, with the backend still stopped:

```powershell
New-Item -ItemType Directory -Force data\reliability-test | Out-Null
npm run dev:browser
```

Open `http://127.0.0.1:5173`. Confirm the app reports that the local backend is unavailable, does not claim it is connected, and offers a retry action. Keep this browser open.

In Terminal 2, start the backend against a separate disposable database and log:

```powershell
$env:DATABASE_PATH = Join-Path (Get-Location) "data\reliability-test\pomas-reliability.db"
$env:LOG_FILE = Join-Path (Get-Location) "logs\reliability-test-backend.log"
npm run dev:backend
```

Use the browser's Retry connection action. Complete owner setup if this disposable database is new. Create a clearly named test farm and at least one test batch. Record the batch code and verify it appears in the Flocks page.

Test a temporary outage during a read workflow:

1. Navigate to Flocks and confirm the test batch is visible.
2. Stop only the test backend with Ctrl+C in Terminal 2. Do not close the browser.
3. Wait for the connection state to change. Confirm the UI does not show Connected and that it provides a clear outage message and retry action.
4. Start the backend again in Terminal 2 using the same `DATABASE_PATH` and `LOG_FILE` values. Do not change the path.
5. Wait for the UI to report that the backend is responding again. Use Retry connection to reload data.
6. Confirm the previously committed test batch still appears.

Test a temporary outage during a write workflow:

1. Open a form for a new test record and enter distinctive values, but do not submit yet.
2. Stop the backend in Terminal 2.
3. Confirm the form remains on screen and the entered values remain present. Confirm the UI clearly reports the outage.
4. Restart the backend with the same disposable database path, then use Retry connection.
5. Confirm the unsaved values remain. Submit once and wait for the success result.
6. Stop and restart the backend again using the same path, retry the connection, and verify the committed record appears exactly once.

Test a delayed startup by repeating the browser-first sequence with a fresh, separately named disposable database file. Leave the browser running before starting the backend, then confirm retry works after the backend becomes ready. Keep each test database isolated and never reuse a real farm database.

Record the observed results for startup delay, failed reads, failed writes, unsaved input retention, and committed-record persistence. This is a manual runtime drill; passing unit tests or seeing zero browser localStorage keys is not proof of SQLite persistence. Keep the disposable database and logs until the results have been reviewed, then archive or remove them manually only after confirming their paths are test-only.

## Layer boundaries

Electron owns lifecycle and native desktop capabilities.

React owns presentation and input handling.

Spring Boot owns validation, authorization, calculations, transactions, audit writes and persistence.

SQLite owns durable local storage.

Never bypass the API from the renderer.

## Database rules

Never edit an applied Flyway migration.

For a schema change, create the next migration:

```
backend/src/main/resources/db/migration/V21__description.sql
```

Do not add a mutable `current_birds` or similar derived balance just to make the UI faster without first proving the query requirement and preserving one authoritative calculation path.

Money is integer minor units.

Audit records are append-only.

Historical transactions should remain interpretable after settings change.

## Adding a feature

Use this order:

1. Define the business fact being recorded.
2. Define the authoritative database representation.
3. Add a forward-only migration if required.
4. Add DTO validation.
5. Implement service/domain rules.
6. Add repository access.
7. Add audit behavior where the change is material.
8. Add integration tests.
9. Add or update API client functions.
10. Add UI state and forms.
11. Add frontend tests.
12. Update documentation.
13. Run the relevant focused tests.
14. Run the full verification suite before release.

Do not put business calculations into React just because the formula looks short.

## Security rules

Production backend binds to localhost.

The renderer runs with:

- context isolation enabled
- Node integration disabled
- sandbox enabled

Do not expose arbitrary filesystem or shell APIs through preload.

Do not put passwords, tokens, database files, backups or real farm data into Git.

Do not add a CDN dependency for core application functionality.

## Data locations

Development database:

```
project/data/poultry.db
```

Packaged production database:

```
%APPDATA%/Poultry Farm Manager/data/poultry.db
```

Packaged backend logs are stored under the application's user-data directory in `logs/backend.log`.

Development logs are stored under the repository application's `logs/backend.log` path.

## Working with documentation

The `doc/` directory contains phase implementation notes.

The `docs/` directory contains durable developer/operator documentation.

When behavior changes, update the durable documentation in `docs/` rather than leaving the repository dependent on old phase notes.


## Renderer font audit

Run the renderer font audit as part of the production build:

```powershell
npm run build
```

The build invokes `scripts/audit-font-assets.mjs` after bundling. The script checks source and production-renderer assets for unreviewed font files, `@font-face`, CSS imports, remote font providers, and font-file URLs, and prints the font file count and byte total. See [font-audit.md](font-audit.md) for the inventory, retained font use, and manual development/packaged DevTools verification steps.

The current UI uses system fonts only. Do not add a web font, KaTeX, or a font dependency based only on a browser audit report. First identify the actual asset/request and its initiator, document which UI needs it, and review the packaged build as well as development mode. Use `font-display: swap` only if a deliberately retained web-loaded font is introduced.


## Supported window size and responsive layout

Pomas is a **Windows desktop application**, not a phone/tablet web app. The Electron window opens at 1366×768 and enforces a minimum of **1100×650 CSS pixels**. The renderer's minimum canvas width matches that supported minimum. Browser/device emulation below 1100px is useful for checking graceful clipping and scroll behavior, but it is not a supported application layout and must not be described as mobile support.

See [responsive-layout-audit.md](responsive-layout-audit.md) for the source findings, supported-width test matrix, and manual browser/packaged-window validation checklist. Test the packaged Electron window by resizing it down to its minimum dimensions; do not add a hamburger menu unless runtime testing identifies a real navigation failure within the supported range.


## Connection status UX validation

The top-bar status pill is the only persistent connection-state indicator. The sidebar connection label and bottom status bar were removed to avoid repeating the same state in three places. The top bar distinguishes connecting, connected, and unavailable. When the backend is unavailable, or becomes available again while displayed farm data may be stale, the recovery alert provides the distinct explanation and Retry connection action.

Manual validation (run against a disposable database, never real farm data):

1. **Connecting:** start the browser renderer before the backend. Confirm the top bar says “CONNECTING TO BACKEND” while the first health check is pending.
2. **Unavailable:** leave the backend stopped until the health check completes. Confirm the top bar says “BACKEND UNAVAILABLE” and the recovery alert explains that the local backend is unavailable and provides Retry connection.
3. **Connected:** start the backend and retry. Confirm the top bar says “BACKEND CONNECTED” after recovery and the recovery alert clears only after the retry flow completes successfully.
4. **Connection loss:** stop the backend after connecting. Confirm the top-bar state changes to unavailable and the recovery alert stays visible.
5. Repeat at the supported desktop minimum (1100×650) and default window size (1366×768). Check the top-bar pill and recovery action remain visible and do not overlap the theme/date controls.

Source edits do not prove these runtime states; record them as **Not run** until tested in the browser and packaged Electron app.


## Audit activity display validation

The dashboard formats audit timestamps for display in the farm's configured timezone (default: `Africa/Lagos`) and humanizes action/entity labels in the renderer. Stored audit events are not changed. Missing timestamps display as `—`; invalid timestamps display as `Unknown time`; missing action/entity values use explicit fallback labels. Unknown non-empty action/entity values are converted to readable title case.

Run the focused formatting and dashboard tests:

```powershell
npm test -- auditFormatting
npm test -- App.test.jsx
```

Manual seeded-data check:
1. Use a disposable test database and create a farm, producing a `CREATE` / `FARM` audit event.
2. Confirm the dashboard shows a readable local timestamp (for `Africa/Lagos`, UTC timestamps display one hour ahead) and labels such as `Created` and `Farm`.
3. Include a timestamp with fractional seconds beyond milliseconds, a missing timestamp, an unknown action/entity, and an empty reason. Confirm safe fallback text and no raw nanosecond timestamp is shown.
4. Inspect the audit table at the supported 1100×650 minimum and 1366×768 default sizes. The table may scroll internally for long content, but must not cause page-level horizontal overflow.
5. Verify the original audit records remain unchanged in the disposable database.

Automated test coverage does not substitute for the manual browser/Electron width check. Record runtime validation only after performing it.
