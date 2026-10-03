# Backup and restore

Backups are created by the Spring Boot backend using SQLite `VACUUM INTO`, which produces a consistent snapshot of a live database. The default naming pattern is `poultry-YYYY-MM-DD-HHmm.db`, with a maximum of 30 retained backup files.

API:
- POST `/api/v1/backup` with optional `{"directory":"C:\\path\\to\\backups"}`
- POST `/api/v1/backup/validate` with `{"file":"C:\\path\\to\\backup.db"}`

Validation runs SQLite `PRAGMA integrity_check` and reads the latest Flyway schema version. A production restore must stop the Spring Boot process before replacing the live database, then restart it and perform the health check. The backend intentionally does not replace its own live database file while its JDBC pool is active.


## Desktop restore orchestration

The renderer does not replace database files. Electron owns the restore IPC flow:

1. The user selects a database backup through the native file picker.
2. Spring Boot validates SQLite integrity and reports the backup Flyway version.
3. Electron compares the backup schema version with the running schema and rejects newer backups.
4. Electron stops Spring Boot.
5. Electron creates a pre-restore safety copy of the live database.
6. Electron replaces the database file.
7. Electron starts Spring Boot again and waits for the health check.
8. If restart fails, the safety copy is restored and the backend is started again.

Manual backup destination selection is also performed through the native Electron dialog, while the actual snapshot remains backend-controlled.

Automatic scheduled backups and a persisted backup-settings screen remain a release-hardening item.
