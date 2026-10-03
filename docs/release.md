# Release

## Release gates

1. Backend tests pass.
2. Backend verification passes.
3. Frontend lint/tests/build pass.
4. Electron build passes.
5. A production Java runtime is generated with `jlink`.
6. The backend JAR is included in the installer resources.
7. Fresh Windows installation works.
8. Existing application data survives an upgrade.
9. Backup creation and backup validation work.
10. Restore is tested with the application stopped before database replacement.
11. A realistic Layer and Broiler farm scenario matches independently calculated expected results.
12. No database, backup, credentials or real farm data is committed.

## Production package

```text
PoultrySetup.exe
  ├── Electron shell
  ├── React renderer
  ├── Spring Boot JAR
  └── bundled Java runtime
```

The installed application stores its live database outside the application bundle.

## Upgrade testing

For every schema release:

```text
old database
   ↓
install new application
   ↓
start application
   ↓
Flyway migration
   ↓
health check
   ↓
verify old records
```

Failed migration behavior must also be tested before release.
