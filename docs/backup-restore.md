# Backup and restore

Backups are created by the Spring Boot backend using SQLite `VACUUM INTO`, which produces a consistent snapshot of a live database. The default naming pattern is `poultry-YYYY-MM-DD-HHmm.db`, with a maximum of 30 retained backup files.

API:
- POST `/api/v1/backup` with optional `{"directory":"C:\\path\\to\\backups"}`
- POST `/api/v1/backup/validate` with `{"file":"C:\\path\\to\\backup.db"}`

Validation runs SQLite `PRAGMA integrity_check` and reads the latest Flyway schema version. A production restore must stop the Spring Boot process before replacing the live database, then restart it and perform the health check. The backend intentionally does not replace its own live database file while its JDBC pool is active.
