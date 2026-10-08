# Backup and restore

Poultry Farm Manager uses SQLite locally. Backup and restore are designed around SQLite's consistency requirements rather than treating the database as an ordinary static file.

## Backup creation

The backend creates backups with SQLite `VACUUM INTO`.

This is preferable to blindly copying a live SQLite file because the database may be using WAL mode.

The default filename pattern is:

```
poultry-YYYY-MM-DD.db
```

Collisions receive a timestamp and, if necessary, a numeric suffix.

Managed automatic backups retain up to 30 backup files.

## Manual backup

The application can request a backup through:

```
POST /api/v1/backup
```

with an optional directory:

```json
{"directory":"C:\\farm-backups"}
```

The renderer can choose the directory through a native Electron folder picker.

## Automatic backup settings

Each farm has persisted backup settings:

- enabled
- directory
- interval

Settings are stored in `backup_settings`.

Automatic backups are disabled by default. When enabled, the scheduler checks the configured directory and creates a new backup once the configured interval has elapsed since the latest managed backup.

The scheduler polls hourly so settings can change without requiring a backend restart. The configured interval is still the source of truth.

## Backup validation

Validation is available through:

```
POST /api/v1/backup/validate
```

The backend checks the selected database for SQLite integrity and Grantino/Flyway schema compatibility.

A valid backup must have a usable successful schema version and must not represent a schema newer than the running application during restore.

## Restore architecture

The renderer does not replace database files.

Electron owns the destructive part of restore:

```
select backup
    ↓
backend validates backup
    ↓
compare schema versions
    ↓
operator confirmation
    ↓
backend creates pre-restore safety snapshot with VACUUM INTO
    ↓
stop Spring Boot
    ↓
copy selected backup to temporary file
    ↓
replace live database
    ↓
start Spring Boot
    ↓
health check
    ↓
resume application
```

The safety snapshot is created while Spring Boot still owns the SQLite connection, so any committed WAL data is included. Spring Boot is then stopped before the live database is replaced because its JDBC pool must not still have the database open.

## Restore failure recovery

If replacement has started and the restarted backend cannot become healthy, Electron attempts to restore the pre-restore safety database and starts the backend again.

The restore flow also removes stale SQLite WAL/SHM files associated with the replaced database so old journal state cannot be mixed with the restored database.

## Safety considerations

- Never restore the active database file over itself.
- Newer-schema backups are rejected.
- Restore requires explicit operator confirmation.
- The pre-restore safety copy is created through the backend's SQLite `VACUUM INTO` backup path before Spring Boot is stopped. Electron never raw-copies the live database for the safety snapshot.
- Restore is not exposed as an arbitrary renderer filesystem API.
- Backup and restore errors are surfaced without exposing secrets.
- Real backups must not be committed to Git.

## Locations

The live production database is:

```
%APPDATA%/Poultry Farm Manager/data/poultry.db
```

Automatic backup location is whatever is configured under Settings. Manual backups can be placed in any accessible operator-selected directory.

The pre-restore safety copy is created beside the live database during restore.

## Recommended farm procedure

For a production farm:

1. Enable automatic backups.
2. Select a backup destination on a different physical drive when practical.
3. Keep an additional manual/off-machine copy.
4. Periodically validate a backup.
5. Perform a restore drill before trusting the backup process with irreplaceable records.
6. Do not edit backup database files manually.

A backup that has never been restored is a hypothesis, not a backup.
