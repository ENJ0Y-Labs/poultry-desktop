# Decision: CSV import architecture

Date: 2026-10-26

Status: **Proposed; requires owner approval before implementation**

Related issue: [#73](https://github.com/ENJ0Y-Labs/poultry-desktop/issues/73)

## Context

Pomas is a local-first farm ledger. Its database stores facts and transactions; bird counts, egg balances, feed stock, inventory stock, and costs are derived from those records. CSV import must preserve the same rules as ordinary entry.

Writing imported rows directly to production tables is not acceptable. It could bypass feed FIFO allocation, the carried bird-cost pool, date-by-date population validation, egg sellable-balance checks, inventory movement rules, and audit history. A successful CSV parse is not proof that the data is valid for a farm.

This decision deliberately defines an architecture only. No import implementation should begin until the owner approves this ADR and the relevant roadmap stage is authorized.

## Decision

Build a backend-owned, preview-first import workflow. The renderer selects a CSV file and reviews its interpretation; Spring Boot owns parsing, validation, domain operations, transaction boundaries, backup coordination, and audit records.

The import pipeline is:

1. Select an approved import type and CSV template.
2. Parse the file without writing production records.
3. Map headers to the selected template's fields.
4. Validate file shape, references, values, dates, and domain constraints.
5. Detect exact re-imports and likely duplicate rows.
6. Simulate the proposed records in chronological order against the current farm state.
7. Present a dry-run preview with accepted rows, blocking errors, warnings, calculated consequences where available, and source row numbers.
8. Create and validate a pre-import SQLite backup.
9. Revalidate against current state, then apply all selected rows through domain services inside one database transaction.
10. Write import traceability and audit information in the same transaction; commit only if every selected operation succeeds.
11. Return a result summary tied to the import ID and backup path.

The UI must never connect to SQLite or write production tables. Controllers remain thin. The import coordinator may orchestrate services but must not reimplement their business rules.

## Supported formats and first release scope

Version 1 accepts CSV only:

- UTF-8 text, with or without a UTF-8 byte-order mark.
- Comma delimiter and standard quoted fields, including escaped quotes and embedded commas/newlines.
- One header row.
- Business dates in ISO `YYYY-MM-DD` format, interpreted as farm-local dates, not UTC timestamps.
- Locale-neutral numeric fields. Money is entered in NGN with at most two decimal places and converted to integer kobo at the boundary.
- No macros, formulas, executable content, remote fetches, or automatic opening of imported files.

The proposed first release supports:

1. **Opening data/balances:** batch opening data and explicit, dated opening-balance events for domains that have an approved domain operation. An opening balance is a historical baseline, not permission to overwrite a calculated current balance. Feed opening stock must preserve quantity and unit cost lots; bird and egg opening data must use domain-supported events and pass the same population/cost/egg invariants as ordinary records.
2. **Mortality:** batch, date, quantity, and any supported reason/notes fields.
3. **Egg collections:** Layer batches only, with good and cracked quantities kept distinct.
4. **Sales:** supported Layer egg sales and Broiler bird sales, using existing customer, price, stock, and population rules.
5. **Expenses:** date, category, amount, and supported description/batch association.

The importer must reject unsupported record types rather than silently ignoring them. Generic inventory movement imports, feed purchases/usage, culling, transfers, health records, and arbitrary full-database replacement are outside the initial scope unless separately approved. Feed opening balances are particularly sensitive and cannot be represented as a single quantity without preserving FIFO lots and their costs.

## Column mapping and file validation

Each import type has a versioned template that defines required fields, optional fields, types, accepted values, and field meanings. The importer may recognize documented header aliases and let the operator map remaining columns to template fields. It must not guess silently.

Validation is layered:

- **File-level:** encoding, delimiter/quoting, header presence, duplicate or ambiguous headers, row/field limits, and unsupported columns where their meaning could affect correctness.
- **Field-level:** required values, string lengths, positive/non-negative quantity rules, integer precision, money precision, valid enumerations, and ISO dates.
- **Reference-level:** batch, customer, and category references must resolve unambiguously. Names that match multiple records are blocking errors; the importer must not create near-duplicate master data silently.
- **Domain-level:** use the same validation and business rules as normal application writes, including batch type/status, placement dates, stock availability, sale limits, and configured units.
- **Chronology-level:** evaluate affected records in business-date order and validate the running balance at every affected date, not only the final total.

Errors identify the CSV row, column/field, rejected value where safe, and a specific corrective action. Errors must not expose stack traces, SQL, credentials, or sensitive filesystem details. Warnings are distinct from blocking errors.

## Duplicate detection and repeatability

Every import attempt receives an import ID. Store a cryptographic hash of the original file and a hash of the normalized mapping/template version.

- An identical file with the same mapping that was already committed is rejected as an already-imported file by default.
- Duplicate rows within one file are reported during preview.
- Rows matching existing records are flagged as possible duplicates when the domain provides a reliable comparison key. A match is not automatically discarded where identical events can legitimately occur more than once.
- Any deliberate inclusion of a flagged possible duplicate requires explicit operator confirmation and a recorded reason.
- The final commit rechecks duplicate conditions inside the transaction to reduce the chance of double import after a preview or retry.

The import history must distinguish parsing/preview attempts from committed imports. A failed or rolled-back import must not be recorded as a successful import.

## Dry-run preview and commit contract

Dry run is read-only. It shows:

- selected file name, file hash, template version, and import ID
- total rows, rows ready to import, blocking rows, warning rows, and likely duplicates
- row-specific errors and corrective guidance
- the affected batch/domain and business date
- domain-derived impact summaries where the existing services can calculate them reliably

A preview is not a reservation of database state. Before commit, the backend must revalidate references, duplicate status, chronology, and all domain rules against current database state. If the state has changed since preview and the import is no longer valid, commit is rejected and a new preview is required.

By default, a file with blocking errors cannot be committed. The operator may correct the source file and preview it again. If the UI supports excluding rows, the exact selected row set must be previewed again and committed as one atomic unit; silently skipping invalid rows is prohibited.

## Chronology and domain-service boundaries

The importer sorts records by business date and uses a deterministic ordering consistent with existing domain rules. It must not invent competing rules for same-day events. Where same-day ordering affects correctness, the relevant domain service owns the order and must document/test it.

All writes must go through existing domain services or new, narrowly scoped domain operations added and tested before import is enabled:

- **Population:** validate the running bird balance at every affected date. Mortality remains distinct from culling and sales. A sale is not mortality.
- **Feed and bird cost:** preserve feed FIFO lot prices and the carried bird-cost pool. Do not import a fabricated current stock or cost total as a substitute for dated transactions.
- **Egg ledger:** preserve good and cracked collection history separately; cracked eggs are not sellable. Egg sales cannot exceed good eggs available under the established chronology rules.
- **Sales and expenses:** use existing money precision, customer/category references, pricing and sale validation, and audit behavior.
- **Inventory/opening balances:** use supported inventory movements or explicit domain opening operations; never set a mutable current-stock field.

If a domain does not expose an operation that can safely represent the imported fact, that import type is blocked until the domain operation and tests exist. Import code must not fall back to repository SQL.

## Atomicity, backup, recovery, and rollback

One import commit is one database transaction. All selected domain writes, import-history status, and related audit records commit together. If any operation fails, the transaction rolls back completely; partial success is not allowed.

Before the transaction, create a consistent SQLite snapshot through the existing backup service using SQLite `VACUUM INTO` (or the repository's approved SQLite backup mechanism), not a raw copy of a live database file. Validate the backup using the existing backup-validation path. If backup creation or validation fails, the import does not start.

The transaction is the first rollback mechanism. The pre-import backup is the recovery mechanism if database integrity or application health is found to be compromised after a commit. Recovery must use the existing coordinated restore workflow, including stopping the backend before replacing the database. The importer must not replace the live database file itself or attempt an unsafe in-process file rollback.

The UI must clearly show whether an import was not started, rolled back, committed, or requires recovery. Do not report success until the backend confirms commit. Retain the safety backup according to the existing operator-controlled backup policy; do not silently delete it immediately after success.

## Audit and traceability

Use audit source **`IMPORT`** for imported domain changes. Each committed import must be traceable to:

- import ID and committed status
- original file name and cryptographic hash
- template/mapping version and normalized mapping hash
- operator/account responsible
- start and completion timestamps
- row numbers and source identifiers for imported records
- record type and created domain-record identifiers
- warning/duplicate confirmations and their reasons
- pre-import backup location/reference
- final counts of inserted records and rejected/excluded rows

Audit data is append-only. Do not store the full CSV in audit records. Treat the source file and backup as local farm data, and do not upload either to a cloud service. Credentials and full sensitive row contents must not be written to logs.

## Alternatives considered

### Direct CSV-to-table insertion

Rejected. It bypasses domain validation, chronology, costing, audit behavior, and derived-state invariants.

### Import each row in its own transaction

Rejected. A failure halfway through leaves a farm with only part of the operator's intended import.

### Accept any spreadsheet export and infer its meaning

Rejected. Column and unit guesses can silently change bird, egg, stock, and money values. Versioned templates and explicit mapping are safer.

### Implement every domain in one release

Rejected. Feed costing, population history, egg inventory, inventory movements, and sales have different invariants. The first release must remain narrow and each supported type must have domain-level tests.

## Consequences

Positive:

- Imported records follow the same rules as manually entered records.
- Preview and row-specific errors allow correction before production data changes.
- Atomic commits prevent partial imports.
- File hashes, source rows, audit events, and backups make imports traceable and recoverable.
- The local-first/offline product model is preserved.

Costs and constraints:

- The preview and commit paths require a reusable validation/orchestration layer.
- Every supported import type needs domain-service integration and tests.
- Some opening balances cannot be supported until a domain-specific opening operation exists.
- Preview results may become stale and must be revalidated at commit.
- Backup creation adds time and local disk usage to each import.

## Approval and implementation gates

This ADR is **proposed**, not approved. The owner must approve the scope and rules before implementation.

After approval:

1. Confirm the supported opening-balance domains and the exact first-release templates.
2. Add domain operations for any supported opening event that cannot be represented safely today.
3. Implement and test parsing/mapping and dry-run validation without production writes.
4. Implement atomic commit, audit traceability, backup validation, and failure recovery.
5. Add integration tests for duplicate imports, mid-commit failure, chronology violations, and backup failure.
6. Run tests against disposable databases only. Never reset or use real farm data for import testing.
7. Do not begin this later-stage feature until the owner explicitly authorizes the roadmap stage.

## Acceptance checklist

- [x] Supported import types and CSV format are defined.
- [x] Column mapping and file/field/reference validation are defined.
- [x] Duplicate detection and chronology rules are defined.
- [x] Dry-run preview and actionable error reporting are defined.
- [x] One atomic transaction per import is required.
- [x] Audit source and traceability are defined.
- [x] Domain-service boundaries for feed costing, population, eggs, sales, and inventory are explicit.
- [x] Backup, recovery, and rollback are defined.
- [x] First-release scope is intentionally limited.
- [ ] Owner approval recorded before implementation.
