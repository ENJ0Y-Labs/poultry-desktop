# Architecture

Poultry Farm Manager is a local-first desktop system:

```
Electron
   |
   +-- main process
   |     +-- backend lifecycle
   |     +-- native dialogs
   |     +-- restore orchestration
   |
   +-- preload
   |     +-- small validated IPC surface
   |
   +-- React renderer
         |
         | HTTP
         v
   Spring Boot REST API
         |
         +-- controllers
         +-- services
         +-- calculation/domain layer
         +-- repositories
         +-- Flyway
         |
         v
       SQLite
```

## Responsibilities

### Electron main process

Electron is responsible for desktop concerns, not farm business logic.

It:

- determines development versus packaged paths
- creates the application data directory
- starts and stops Spring Boot
- waits for `GET /api/v1/health`
- manages backend logging
- exposes native backup-directory and restore dialogs
- performs the file replacement part of database restore
- blocks arbitrary renderer navigation

Packaged Java is loaded from the application resources. During development the Maven Wrapper starts Spring Boot.

### Preload

The renderer does not receive unrestricted Node.js access.

The preload exposes only the small desktop operations required by the UI, including:

- choose a backup directory
- request database restore

IPC handlers reject unexpected arguments.

### React renderer

React owns:

- presentation
- form state
- navigation
- loading, empty and error states
- user input
- API calls through `app/renderer/src/services/api.js`

The renderer must not:

- open SQLite
- run SQL
- calculate authoritative farm totals
- read arbitrary files
- access Node.js APIs directly

The rule is: **UI → API, not UI → database.**

### Spring Boot backend

Spring Boot owns:

- request validation
- authentication and authorization
- domain rules
- calculations
- transactions
- audit records
- repositories and SQL
- Flyway migrations
- backup creation and validation
- reports and exports
- backend-derived attention alerts

Controllers are intentionally thin. Services own business rules.

### SQLite

SQLite is the production data store. The backend configures foreign keys, WAL journaling and FULL synchronous mode.

The database is local to the installed machine. There is no cloud database dependency.

## Startup lifecycle

Development:

```
Electron
  -> create data/log directories
  -> start Maven Spring Boot
  -> poll /api/v1/health
  -> open React window
```

Packaged:

```
Electron
  -> create %APPDATA%/Poultry Farm Manager/data
  -> start bundled Java + poultry-backend.jar
  -> bind Spring Boot to 127.0.0.1:18942
  -> poll /api/v1/health
  -> open React window
```

The window is not opened until the backend is healthy.

## Shutdown

Electron requests backend shutdown before application exit. Spring Boot uses graceful shutdown with a ten-second shutdown phase. The backend owns its database connections, so the application does not blindly copy a live SQLite database during ordinary backup.

## Security boundary

Production Spring Boot binds to `127.0.0.1`.

Electron uses:

- context isolation
- Node integration disabled
- sandbox enabled
- a restricted preload bridge
- a local-content CSP
- restricted navigation

Authentication uses HTTP-only, SameSite=Strict session cookies. Passwords are stored as PBKDF2-HMAC-SHA256 hashes with random salts.

The backend rejects unauthenticated production application requests except for the bootstrap/login/health paths required for startup and first-time account setup.

## Data flow

A normal write follows:

```
React form
  -> API client
  -> REST controller
  -> validation
  -> service/domain rules
  -> transaction
  -> repository/SQLite
  -> audit where required
  -> response
  -> UI refresh
```

A calculation follows:

```
authoritative records
  -> domain/calculation service
  -> derived result
  -> dashboard/report/API
```

Derived values such as current birds and stock are not treated as independently editable facts.

## Migration architecture

Flyway migrations are stored under:

```
backend/src/main/resources/db/migration
```

Migrations are forward-only. The current migration series reaches V20, which adds application settings, backup settings and configurable expense categories.

## Packaging architecture

Electron Builder creates a Windows NSIS installer. The package contains:

- Electron main/preload/renderer application files
- built renderer assets
- Spring Boot JAR
- bundled Java runtime

The live database remains outside the application bundle so upgrades do not overwrite farm data.
