# Pomas

Pomas is a local-first Windows desktop application for managing Layer and Broiler poultry operations. It records farm facts, calculates derived farm state, tracks production and costs, provides dashboards and reports, and supports SQLite backup and restore.

The application is designed for farm use without a cloud dependency. The packaged application runs its own Electron shell, React renderer, Spring Boot backend, SQLite database, and bundled Java runtime.

## What it does

- Farm, house, supplier, customer and batch management
- Layer and Broiler flock tracking
- Mortality, culling, transfers and bird sales
- Daily farm operations and water usage
- Feed types, purchases, usage, inventory and FIFO costing
- Health, drug and vaccination records
- Egg collection, inventory and sales
- Broiler weights, growth, FCR and sales
- Expenses and configurable expense categories
- Inventory and stock movements
- Pricing and margin calculations
- Farm and batch dashboards
- Backend-derived attention alerts
- Farm and batch reports
- CSV and PDF report export
- User authentication and account management
- Append-only audit history
- SQLite-safe backup, validation and desktop restore
- Farm, application and backup settings

The core design rule is simple: records are facts, derived state is calculated, and historical transactions are not silently rewritten.

## Architecture

```
Electron desktop shell
        |
        v
React renderer
        |
        | HTTP, localhost only
        v
Spring Boot REST API
        |
        +--> validation / authorization
        +--> calculation services
        +--> transactions / audit
        +--> Flyway migrations
        |
        v
SQLite database
```

The renderer does not access SQLite or Node.js directly. Electron owns the desktop lifecycle, native file dialogs, backend startup/shutdown and restore orchestration. Spring Boot owns business rules, calculations, persistence, migrations and database access.

See [docs/architecture.md](docs/architecture.md).

## Requirements

Development:

- Windows is the primary supported desktop platform.
- Node.js 22.12+
- npm
- JDK 17
- Git
- The repository includes Maven Wrapper, so Maven does not need to be installed separately.

For packaging, `JAVA_HOME` must point to a JDK 17 installation containing `jlink`.

End users of a packaged Windows installer do not need Node.js, npm, Maven or Java installed separately. The installer includes the application and a trimmed Java runtime.

## Development setup

Clone the repository and enter it:

```bash
git clone https://github.com/ENJ0Y-Labs/poultry-desktop.git
cd poultry-desktop
```

Install Java 17 and set `JAVA_HOME`. Verify:

```powershell
java -version
$env:JAVA_HOME
```

Install JavaScript dependencies:

```bash
npm install
```

The backend uses SQLite and Flyway. No PostgreSQL, MySQL or cloud database is required.

## Run locally

### Browser development (recommended)

The browser workflow is the current recommended development path while the Electron development launcher is being investigated.

Start the Spring Boot backend and Vite frontend together:

```powershell
npm run dev:browser:all
```

Then open:

- **Frontend:** [http://127.0.0.1:5173/](http://127.0.0.1:5173/)
- **Backend API:** [http://127.0.0.1:18942/api/v1](http://127.0.0.1:18942/api/v1)

The command runs the existing `dev:backend` and `dev:browser` scripts concurrently. The backend uses the local SQLite development database at `data/poultry.db` by default. Vite provides React Fast Refresh and Hot Module Replacement (HMR), so supported React and CSS edits appear in the browser without rebuilding or packaging the desktop application.

The browser frontend connects to the local backend. Keep both processes running while developing. Stop them with **Ctrl+C** in the terminal.

You can also run the services separately in two terminals:

```powershell
# Terminal 1
npm run dev:backend
```

```powershell
# Terminal 2
npm run dev:browser
```

### Troubleshooting a port conflict

The backend uses port `18942` and Vite uses port `5173`. If a port is already occupied, first identify the process listening on it:

```powershell
Get-NetTCPConnection -LocalPort 18942 -State Listen |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

Inspect the process before stopping it:

```powershell
Get-Process -Id <PID>
```

If it is an old development backend process that you own and no longer need, stop only that process:

```powershell
Stop-Process -Id <PID> -Force
```

Replace `<PID>` with the process ID returned above. Do not terminate every `java.exe` process; other applications may be using Java.

### Electron desktop development

The normal Electron development command is:

```powershell
npm run dev
```

**Current limitation:** In the investigated environment using Node.js `v26.13.0`, Electron `38.2.1` and electron-vite `4.0.1`, this command fails during Electron binary path detection with an `Electron uninstall at getElectronPath` error. The Electron development window therefore has not been verified in that environment.

Use `npm run dev:browser:all` for routine React UI and backend development. Browser mode does not expose Electron-only capabilities such as native file dialogs, filesystem access through the preload bridge, or desktop IPC. Those features still require testing in the actual Electron runtime once its development launcher is working.

Do not package the application for ordinary UI development. Production packaging is a separate release workflow documented below.

## Testing and quality checks

Frontend tests:

```bash
npm test
```

Frontend lint:

```bash
npm run lint
```

Backend tests:

```bash
npm run backend:test
```

Backend verification:

```bash
npm run backend:verify
```

Frontend production build:

```bash
npm run build
```

A useful pre-release sequence is:

```bash
npm test
npm run lint
npm run backend:verify
npm run build
```

Tests must be run locally or by CI before a release is declared healthy. Documentation never substitutes for actually running the test suite, a depressing but necessary fact.

## Building

Build the Electron application without creating an installer:

```bash
npm run build
```

This produces the Electron/Vite build output used by packaging.

## Building and Packaging Pomas for Windows

Create the production Windows package:

```powershell
$env:JAVA_HOME = "C:\Path\To\JDK-17"
npm run package
```

`npm run package` runs the full pipeline: `package:runtime` → `build` → `backend:package` → `package:verify` → `electron-builder`. Individual commands help troubleshoot failures.

### Outputs

| Output | Location | Description |
|---|---|---|
| **Windows installer** | `release/PomasSetup.exe` | NSIS installer |
| **Portable app** | `release/win-unpacked/` | Unpacked app with runtime and JAR |
| **Bundled Java runtime** | `release/win-unpacked/resources/runtime/bin/java.exe` | jlink-generated (no separate Java install needed) |
| **Bundled Spring Boot backend** | `release/win-unpacked/resources/backend/poultry-backend.jar` | Spring Boot application |

### Verification

```powershell
node scripts/verify-production-inputs.mjs   # check modules
node scripts/verify-csp.mjs                 # check CSP
node scripts/verify-a11y.mjs                # check accessibility
```

### Note

Successful packaging does not guarantee a verified clean-machine installation and launch test.
"

Create the production Windows package:

```powershell
$env:JAVA_HOME="C:\Path\To\JDK-17"
npm run package
```

Packaging performs these major steps:

1. Creates a trimmed Java runtime with `jlink`.
2. Builds the React/Electron application.
3. Packages the Spring Boot backend JAR.
4. Runs Electron Builder.
5. Produces the Windows NSIS installer under `release/`.

The bundled runtime includes `java.desktop`, which Spring Boot requires for property
binding, along with the HTTP, security, database, and supporting Java modules.
Packaging verifies these modules before creating the installer.

The production package contains the Electron application, renderer assets, Spring Boot JAR and bundled Java runtime. The development database is not packaged.

## Database location

Development:

```
project/data/poultry.db
```

Production:

```
%APPDATA%/Poultry Farm Manager/data/poultry.db
```

Electron chooses the path based on whether the application is packaged and passes the absolute path to Spring Boot.

SQLite is configured for:

- foreign-key enforcement
- WAL journaling
- FULL synchronous mode
- one database connection in the Hikari pool

Flyway owns schema migrations. Never edit an already-applied migration. Add a new migration.

Do not commit databases, SQLite WAL/SHM files, backups, credentials or real farm data.

## Backup location

Automatic backups are configured per farm under Settings. The default automatic-backup state is disabled and no directory is assumed until the operator configures one.

Manual backups can be written to a directory selected by the operator.

Backups use SQLite `VACUUM INTO` to create a consistent database snapshot. Restore also creates a temporary pre-restore safety copy before replacing the live database.

See [docs/backup-restore.md](docs/backup-restore.md).

## Release process

A release should pass all of these gates:

1. Update the application version consistently in `package.json` and `backend/pom.xml`.
2. Run frontend tests and lint.
3. Run backend verification.
4. Build the Electron application.
5. Generate the bundled Java runtime with `jlink`.
6. Build the Windows NSIS installer.
7. Test a fresh Windows installation.
8. Test an upgrade using an existing database.
9. Confirm Flyway migrates the existing database successfully.
10. Create and validate a backup.
11. Perform a restore drill and verify recovery.
12. Test realistic Layer and Broiler farm data.
13. Verify reports, exports and calculations against independently expected results.
14. Confirm no database, backup, secret or real farm data is included in Git.
15. Publish the installer only after the release gates pass.

See [docs/release.md](docs/release.md).

## Documentation

- [Architecture](docs/architecture.md)
- [Database schema](docs/database-schema.md)
- [API](docs/api.md)
- [Calculations](docs/calculations.md)
- [Backup and restore](docs/backup-restore.md)
- [Development](docs/development.md)
- [Release](docs/release.md)
- [Audit](docs/audit.md)

## License

See [LICENSE](LICENSE).
