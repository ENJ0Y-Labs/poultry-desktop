# Audit

Material changes are recorded in `audit_logs`. The record includes actor, timestamp, action, entity, entity ID, optional batch/farm scope, reason, and before/after JSON snapshots.

Audit rows are append-only. Migration V16 installs SQLite triggers that reject UPDATE and DELETE against `audit_logs`.

The normal application API exposes only read access through:

GET `/api/v1/audit?limit=100`

The application does not expose a normal endpoint for modifying or deleting audit history.


## Transaction correction and void audit trail

A correction or void adds an append-only audit event. It records the authenticated actor, server UTC timestamp, required reason, original transaction type/ID, and replacement reference when applicable. The original transaction facts and prior audit entries are preserved. The reversal relationship, not a destructive edit to the original row, determines whether its business effect is active.

The original and replacement/reversal state change, linked ledger updates, dependent recalculation, and audit append must commit in the same database transaction. Repeated reversal attempts must fail without duplicate effects. See `decisions/2026-10-26-auditable-transaction-corrections.md` for transaction-specific semantics and invariants.
