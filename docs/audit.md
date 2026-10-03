# Audit

Material changes are recorded in `audit_logs`. The record includes actor, timestamp, action, entity, entity ID, optional batch/farm scope, reason, and before/after JSON snapshots.

Audit rows are append-only. Migration V16 installs SQLite triggers that reject UPDATE and DELETE against `audit_logs`.

The normal application API exposes only read access through:

GET `/api/v1/audit?limit=100`

The application does not expose a normal endpoint for modifying or deleting audit history.
