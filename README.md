# Poultry Farm Manager

Local-first Windows desktop poultry management for Layer and Broiler farms.

## Architecture

Electron → React/JavaScript → REST API → Spring Boot → SQLite.

The renderer never accesses SQLite or Node.js directly. Spring Boot owns validation, calculations, persistence, transactions, audit records and database migrations.

## Current implementation

The repository now contains the Stage 1–16 farm domain plus the production-foundation work for audit, authentication, dashboards, attention, reports, backup validation and CI.

The important rule remains: records are facts, derived state is calculated, and historical transactions are not silently deleted.

## Requirements

Development:
- Node.js 20+
- npm
- Java 17 JDK
- Maven wrapper included in the repository

The packaged Windows application is designed to include its own Java runtime. The farmer should not need Node.js, Maven or Java installed.

## Development

Install dependencies:

```bash
npm install
```

Run the desktop app and local Spring Boot backend:

```bash
npm run dev
```

Run frontend tests:

```bash
npm test
```

Run backend tests:

```bash
npm run backend:test
```

Run the full backend verification:

```bash
npm run backend:verify
```

Build the Electron application:

```bash
npm run build
```

Create a production Windows package:

```bash
npm run package
```

Packaging requires a JDK with `JAVA_HOME` set because the build creates a local Java runtime with `jlink`.

## Local backend

Development defaults to:

`http://127.0.0.1:18942`

Production binds to localhost only.

## Data

Development defaults to `project/data/poultry.db`.

Production data belongs under Electron's application-data directory:

`%APPDATA%/Poultry Farm Manager/data/poultry.db`

Do not commit databases, backups, WAL files, or real farm data.

## Backup

Backups are created by the backend using SQLite `VACUUM INTO`, not by blindly copying a live SQLite file. See `docs/backup-restore.md`.

## Documentation

- `docs/architecture.md`
- `docs/database-schema.md`
- `docs/api.md`
- `docs/calculations.md`
- `docs/backup-restore.md`
- `docs/development.md`
- `docs/release.md`
- `docs/audit.md`

## CI

GitHub Actions runs frontend lint/tests/build, backend tests/verification and an Electron build on pushes and pull requests.

This project is not considered production-ready merely because it compiles. Real farm acceptance, upgrade testing, backup/restore drills and installer testing are release gates.
