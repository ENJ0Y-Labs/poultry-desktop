# Phase 43: Windows installer

## Goal

Produce and validate the Windows installer used by farmers.

Production flow:

```text
PoultrySetup.exe
      |
      v
Install
      |
      +--> Desktop shortcut
      +--> Start Menu shortcut
      |
      v
Start Poultry Farm Manager
      |
      v
Electron
      |
      v
Bundled Java runtime
      |
      v
Spring Boot
      |
      v
SQLite
```

The installed application must not require Node.js, npm, Java, Maven, Python or PostgreSQL.

## Installer configuration

Electron Builder uses the Windows NSIS target.

The installer:

- is named `PoultrySetup.exe`
- uses application ID `com.grantinofarms.poultry`
- installs Poultry Farm Manager
- creates a desktop shortcut
- creates a Start Menu shortcut
- runs the application after installation
- permits a custom installation directory
- keeps application data when the application is uninstalled

The last point is intentional. The installed program and the user's farm database are different things. Removing the program must not casually erase the farmer's records.

Application data remains under:

```text
%APPDATA%/Poultry Farm Manager/
    data/
    logs/
```

## Upgrade identity

The same Electron Builder `appId` is retained across versions:

```text
com.grantinofarms.poultry
```

This gives Windows/NSIS a stable application identity for reinstall and upgrade scenarios.

The installation directory contains the application binaries. The application-data directory contains the SQLite database and logs.

Therefore:

```text
Install directory  !=  application-data directory
```

An upgrade replaces application files without replacing the existing farm database.

## CI verification

The Electron CI job now:

1. Builds the bundled Java runtime.
2. Builds Electron/React production assets.
3. Packages the Spring Boot backend.
4. Verifies production inputs.
5. Runs Electron Builder.
6. Verifies `release/PoultrySetup.exe` exists and is not suspiciously small.
7. Uploads the installer as the GitHub Actions artifact `poultry-farm-manager-windows-installer`.

This catches the embarrassing class of release where the build says "success" but nobody actually checked whether an installer came out.

## Installer lifecycle acceptance test

Run these tests on a Windows test machine. Do not use the development machine as the only acceptance environment.

### 1. Fresh installation

Precondition:

- Poultry Farm Manager is not installed.
- No existing application data is present.

Steps:

1. Run `PoultrySetup.exe`.
2. Complete installation.
3. Confirm the desktop shortcut exists.
4. Confirm the Start Menu shortcut exists.
5. Start the application.
6. Confirm Electron starts the bundled backend.
7. Confirm the login/start screen appears.
8. Confirm the SQLite database is created under application data.
9. Create a farm and one representative record.
10. Close the application.

Expected:

- Application starts without system Java.
- Application starts without Node.js/npm.
- Application starts without Maven/Python/PostgreSQL.
- Farm data is persisted.

### 2. Existing installation

Precondition:

- Version is already installed.
- Application data contains real test records.

Steps:

1. Start the existing application.
2. Confirm records are present.
3. Close the application.
4. Run the same installer again.

Expected:

- Installer recognizes the existing installation.
- Installation completes without creating a second application identity.
- Existing application data remains intact.

### 3. Reinstall

Precondition:

- Existing installation and test data.

Steps:

1. Uninstall Poultry Farm Manager.
2. Confirm the application binaries and shortcuts are removed.
3. Check application data.
4. Install `PoultrySetup.exe` again.
5. Start the application.

Expected:

- Program files are removed by uninstall.
- Application data remains.
- Reinstalled application can open the existing database and records.

### 4. Upgrade

Precondition:

- Version A is installed with test data.

Steps:

1. Install a newer version B using the same `appId`.
2. Start version B.
3. Confirm the existing farm and records are present.
4. Confirm Flyway applies only required migrations.
5. Create a new record.
6. Restart version B.
7. Confirm both old and new records remain.

Expected:

- Application identity remains the same.
- User data survives the upgrade.
- Database migrations complete successfully.
- No development database is introduced.

### 5. Uninstall

Steps:

1. Close Poultry Farm Manager.
2. Uninstall it from Windows.
3. Confirm desktop and Start Menu shortcuts are removed.
4. Confirm installed application files are removed.
5. Inspect application data.

Expected:

- Application binaries are removed.
- Farm database and application data are preserved.
- A future reinstall can recover the existing farm data.

## Data-preservation test

Use a known marker record before reinstall/upgrade:

```text
Farm: Grantino Test Farm
Batch: INSTALLER-001
Marker: PRESERVE-ME-43
```

After each lifecycle operation, verify the marker through the application.

The critical invariant is:

```text
application lifecycle operation
        !=
farm data deletion
```

## Phase 43 acceptance

- [x] Windows NSIS installer configured.
- [x] Installer artifact named `PoultrySetup.exe`.
- [x] Desktop shortcut configured.
- [x] Start Menu shortcut configured.
- [x] Application starts after installation.
- [x] Custom installation directory supported.
- [x] Application data deletion on uninstall explicitly disabled.
- [x] CI verifies the installer exists.
- [x] CI uploads the installer artifact.
- [ ] Fresh installation tested on a clean Windows machine.
- [ ] Existing installation tested.
- [ ] Reinstall tested.
- [ ] Upgrade tested across two application versions.
- [ ] Uninstall tested.
- [ ] Application-data preservation verified.
