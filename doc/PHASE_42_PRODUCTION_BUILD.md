# Phase 42: Production build

## Goal

Produce a Windows NSIS installer that contains everything required to run Poultry Farm Manager on a clean Windows machine.

The end user must not install Node.js, npm, Maven, Java, Python, PostgreSQL or another external database server.

## Production package

The installer contains:

- Electron desktop runtime and main process
- React/Vite renderer assets
- Spring Boot backend JAR
- trimmed Java 17 runtime generated with jlink
- packaged application configuration and backend resources
- native Electron resources required by the application

The application database is created in the user's application-data directory at first run. Farm data is not bundled into the installer.

## Packaging pipeline

```text
npm run package
    |
    +--> package:runtime
    |      jlink creates runtime/
    |
    +--> build
    |      electron-vite creates dist/
    |
    +--> backend:package
    |      Maven Wrapper creates backend/target/poultry-backend.jar
    |
    +--> package:verify
    |      verifies renderer, main, preload, backend JAR and Java runtime
    |
    +--> electron-builder
           creates Windows NSIS installer in release/
```

The Maven Wrapper and JDK are build-time requirements only. They are not dependencies of the installed application.

## Runtime startup

In production Electron starts the bundled Java executable:

```text
resources/runtime/bin/java.exe
        |
        v
resources/backend/poultry-backend.jar
        |
        v
Spring Boot
        |
        v
127.0.0.1:18942
```

Electron passes the production database path to Spring Boot. The backend runs with the prod profile.

The production database is stored under:

```text
%APPDATA%/Poultry Farm Manager/data/poultry.db
```

Logs are stored under:

```text
%APPDATA%/Poultry Farm Manager/logs/backend.log
```

## Installer

The production target is Windows NSIS.

The installer:

- uses the product name Poultry Farm Manager
- creates a desktop shortcut
- creates a Start Menu shortcut
- allows the user to choose the installation directory
- produces an artifact named Poultry Farm Manager Setup <version>.exe

## Verification

The package command fails before Electron Builder if any required production input is missing.

The verification checks:

- dist/renderer/index.html
- dist/main/main.js
- dist/preload/preload.js
- backend/target/poultry-backend.jar
- runtime/bin/java.exe on Windows

A production build is not considered complete merely because Electron Builder produced an installer. The installer must also be installed and launched on a clean Windows environment.

## Clean-machine acceptance test

On a Windows machine without development tools:

1. Install the NSIS installer.
2. Launch Poultry Farm Manager.
3. Confirm the application reaches the login/start screen.
4. Confirm the backend starts without Java installed.
5. Confirm the SQLite database is created.
6. Confirm Flyway migrations complete.
7. Create a farm record.
8. Restart the application.
9. Confirm the record remains.
10. Confirm backend logs are written.
11. Create and validate a backup.
12. Confirm restore works.
13. Confirm the application still works with Node.js, npm, Maven, Python and PostgreSQL absent.

## Security boundary

No development database, .env file, credential, backup or real farm data is packaged.

The backend remains loopback-only. The renderer communicates with it through the local HTTP API. Electron keeps Node APIs out of the renderer.

## Phase 42 acceptance

- [x] Electron production packaging is defined.
- [x] React renderer is included.
- [x] Spring Boot JAR is included.
- [x] Bundled Java runtime is included.
- [x] Backend/application resources are packaged.
- [x] End users do not need Node.js.
- [x] End users do not need npm.
- [x] End users do not need Maven.
- [x] End users do not need a system Java installation.
- [x] End users do not need Python.
- [x] End users do not need PostgreSQL.
- [x] Production package inputs are verified before Electron Builder.
- [ ] Clean-machine installation and runtime test must be performed before release.
