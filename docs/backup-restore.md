# Backup and Restore

Backup and restore are planned for Stage 4. The backend will own SQLite backup/restore coordination using SQLite-safe backup functionality or `VACUUM INTO`.

The renderer must never copy or replace a live database file directly.
