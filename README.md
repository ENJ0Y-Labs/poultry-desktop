# Poultry Farm Manager

Poultry Farm Manager is a local-first Windows desktop application for managing Layer and Broiler poultry operations. It records farm facts, calculates derived farm state, tracks production and costs, provides dashboards and reports, and supports SQLite backup and restore.

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
- Node.js 20+
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

Start the Electron desktop application:

```bash
npm run dev
```

Development Electron starts Spring Boot with the Maven Wrapper and uses the development database path:

```
project/data/poultry.db
```

The local backend listens on:

```
http://127.0.0.1:18942
```

The renderer talks to the same localhost API.

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

## Packaging

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
