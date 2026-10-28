# CSV bulk import

## Product requirement

Pomas lets an operator download a module-specific CSV template, fill it in using a spreadsheet editor, upload it, review validation results, and explicitly confirm a safe import. This workflow must work offline. A module is supported only when its template, preview, validation, transactional import, duplicate handling, and result report are implemented and tested.

## Operator workflow

1. Open Import / Export.
2. Choose the target module and download its versioned CSV template.
3. Fill the template in a spreadsheet editor. Keep the header row unchanged.
4. Upload the saved CSV file.
5. Review row count, valid rows, invalid rows, duplicate/conflicting rows, and the preview.
6. Download a row-level error report, correct the file, and retry if validation fails.
7. Confirm import after reviewing the preview.
8. Review the result summary.

Preview must not write data. Invalid input must never be silently ignored.

## Template contract

Each template documents exact header names and column order, required and optional fields, units and accepted values, business date format YYYY-MM-DD where relevant, money input in naira converted by the backend to integer kobo, and stable human-readable references where available. Internal database IDs must not be required. Templates use UTF-8 and standard CSV quoting and contain only replaceable example data.

## Supported modules

| Module | Template version | Fields | Duplicate behavior |
|---|---|---|---|
| Houses / pens | 1.0 | name, code, notes | Exact existing name/code/notes match is skipped; code/name reuse with different details is a conflict. |
| Customers | 1.0 | name, phone, notes | Duplicate names in the file or existing customer names block the import. |
| Suppliers | 1.0 | name, phone, email, address, notes | Duplicate names in the file or existing supplier names block the import. |

The current house/pen model supports name, code, and notes; capacity fields are not part of this model and must not be added to the CSV contract by assumption. House codes are normalized to uppercase by the existing service. Houses/pens are imported before batches because batches reference houses. Batch import remains unavailable until its separate implementation is complete.

## Validation and transaction behavior

- Parse quoted commas, escaped quotes, CRLF/LF, UTF-8 BOM, and empty cells safely.
- Reject malformed headers, duplicate headers, unexpected columns, malformed quoting, oversized files, and excessive row counts with clear errors.
- Validate the entire file before confirmation and report row number, column, and actionable reason.
- Preview reads existing houses to identify exact duplicates and code/name conflicts; it does not write records.
- The backend validates the request again and uses the existing Spring Boot services and database transaction.
- House creation uses the existing service, including normal audit records. Any failure during a confirmed multi-row import rolls back the entire transaction.
- Exact existing house records are skipped, so retrying the same house import does not create duplicates. A matching code or name with different details is a conflict; no implicit update is supported.
- Customer/supplier imports retain their existing duplicate-rejection behavior.
- Do not log raw CSV contents or sensitive customer data. Processing remains local and offline.

## Testing requirements

Frontend tests cover CSV quoting, BOM/line endings, required fields, duplicate names/codes, unsupported headers, and template fields. Backend tests must cover valid house imports, missing fields, duplicate codes within the file, existing-record conflicts, exact duplicate retries, audit behavior, and transaction rollback. Run frontend tests/build/lint and backend Maven tests before merge; runtime validation must be reported separately from automated tests.


## Historical operational templates (v1.0)

Operational imports require the target batch to exist first. Resolve it by its exact human-readable `batch_code`; never supply internal database IDs. Use the actual farm business date in `YYYY-MM-DD` format. Dates before the batch placement date are rejected.

### Daily records

Headers, in order: `batch_code,record_date,notes,water_entries`.

- `batch_code` and `record_date` are required.
- `notes` is optional and remains a note, not a replacement for a missing daily record.
- `water_entries` is optional. Enter configured container capacity and count pairs as `capacity_units:container_count`, separated by semicolons; for example `10:2;20:1`. Capacities must already exist in the farm's water-container settings. Each capacity may appear once per row.
- The import creates one daily-record header for each accepted batch/date. It does not create fake rows for missing historical days.
- Feed usage is deliberately excluded. Feed remains recorded only in the feed subsystem.

An existing batch/date with identical notes and water entries is skipped as a duplicate. Different details for the same batch/date are a conflict, not an update.

### Mortality and culling

Headers, in order: `batch_code,event_date,quantity,reason,notes`.

- `batch_code`, `event_date`, and `quantity` are required.
- `quantity` must be a positive whole number.
- `reason` and `notes` are optional text fields, matching the current domain model.
- Mortality and culling use separate templates and remain separate population event types.
- The backend validates the ordered population history, including later existing events affected by a back-dated import. A negative population on any date blocks the complete file before confirmation.
- An exact repeated event (same batch, date, event type, quantity, reason, and notes) is treated as a duplicate and skipped. The importer does not update or delete an existing event.

The server performs a read-only validation preview. Confirmation validates again and writes through the existing daily-record and bird-population services inside one transaction, preserving their audit behavior. If any confirmed write fails, the transaction must roll back in full.
