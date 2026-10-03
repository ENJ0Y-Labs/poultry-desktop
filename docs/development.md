# Development

## Prerequisites

- Windows for the primary desktop workflow
- Node.js 20+
- npm
- JDK 17
- Git
- PowerShell for the documented Windows commands

The Maven Wrapper is included, so a separate Maven installation is not required.

Packaging additionally requires a valid `JAVA_HOME` containing `jlink`.

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

## Run

Start the desktop application:

```powershell
npm run dev
```

The development backend runs through Maven and uses:

```
project/data/poultry.db
```

The API is:

```
http://127.0.0.1:18942/api/v1
```

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
