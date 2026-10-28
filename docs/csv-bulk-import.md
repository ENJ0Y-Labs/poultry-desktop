# CSV bulk import

## Product requirement

Pomas must let an operator download a module-specific CSV template, fill it in using a spreadsheet editor, upload it, review validation results, and explicitly confirm a safe import. This workflow must work offline.

A template download alone is not a completed import feature. A module is supported only when its template, preview, validation, transactional import, duplicate handling, and result report are implemented and tested.

## Operator workflow

1. Open Settings → Data import (and provide contextual import entry points from supported modules as they are enabled).
2. Choose the target module and download its CSV template.
3. Fill the template in Excel, LibreOffice, or another CSV editor. Keep the header row unchanged.
4. Upload the saved CSV file.
5. Review row count, valid rows, invalid rows, duplicate/conflicting rows, and a readable preview.
6. Download a row-level error report, correct the file, and retry if validation fails.
7. Confirm import after reviewing the preview.
8. Review the result summary and any downloadable report.

The import preview must not write data. Invalid input must never be silently ignored.

## Template contract

Each template must document exact header names and column order; required and optional fields; accepted enum values and units; business date format YYYY-MM-DD; money input in naira converted by the backend to integer kobo; how to reference existing records using stable human-readable codes where available, never internal database IDs; and one clearly marked example row that operators can replace or remove.

Templates must use UTF-8 and standard CSV quoting. Exported examples must not contain real farm or customer data.

## Initial module coverage

Implement in dependency-aware phases. Do not expose a module as importable until its end-to-end path is complete.

### Phase 1: reference data and opening records
- Houses / pens
- Suppliers
- Customers
- Batches / flocks

Batch imports must resolve house and supplier references, enforce unique batch codes, validate bird counts and dates, and preserve the existing opening bird purchase/cost history. Import order must be explicit where records depend on one another.

### Phase 2: operational records
- Daily records
- Mortality and culling
- Feed types, feed purchases, and feed usage
- Health records, drugs/treatments, and vaccinations
- Layer egg collections
- Broiler weight records

The backend must apply the same batch type, date, population, feed FIFO, and domain constraints as normal forms.

### Phase 3: inventory, sales, and finance
- Inventory items and stock movements
- Egg sales and bird sales
- Expenses

Posted transactions must not be duplicated or partially applied. Importing purchases and movements must use the existing transaction services and audit behavior.

## Safety and consistency requirements

- CSV parsing must correctly handle quoted commas, escaped quotes, CRLF/LF line endings, UTF-8 BOM, and empty cells.
- Reject malformed headers, duplicate headers, unexpected columns unless explicitly supported, invalid encodings, and unreasonable file/row sizes with a clear message.
- Validate the entire file before confirmation. Report errors with CSV row number, column, and actionable reason.
- A preview must not mutate persistent data.
- The backend is authoritative. The renderer must not implement farm calculations or write to the database.
- Use a backend transaction for each confirmed import unit. If one row fails in an atomic import, roll back that unit; never leave half-written stock, bird population, sales, egg inventory, costs, or audit records.
- Use a deterministic idempotency/fingerprint strategy to prevent accidental repeat imports. Do not deduplicate unrelated legitimate transactions solely because their values happen to match.
- Enforce existing audit, reversal, FIFO costing, batch lifecycle, and financial invariants.
- Return a summary with accepted, rejected, and duplicate counts, plus row-level reasons.
- Never log CSV contents or sensitive customer data.
- Keep all processing local and usable without internet.

## UX requirements

- Show the chosen module and template version before upload.
- Display total rows, valid rows, invalid rows, and duplicates/conflicts.
- Show a bounded preview, with the full validation report downloadable as CSV.
- Disable confirmation while validation is running or any blocking validation error exists.
- Require explicit confirmation before writes.
- Preserve the selected file and validation report when the user returns to correct data, where practical.
- Make progress and failure states understandable to a farm operator.

## Testing requirements

### CSV parser tests
- quoted commas, quotes, line endings, BOM, blank lines, empty values;
- malformed quoting, duplicate/missing headers, unexpected headers;
- empty file, header-only file, oversized input, and row limits.

### Validation tests
- required fields, numbers, dates, enum values, references, duplicate codes, and row-level error locations;
- layer-only egg records and broiler-only weight records;
- invalid batch dates and population balances;
- money conversion to integer kobo.

### Transaction and regression tests
- preview causes no writes;
- successful import is reflected in the normal list/detail views;
- a failed import rolls back its transaction;
- retrying an import does not duplicate records;
- feed purchases/usage preserve FIFO balances and costs;
- mortality, culling, and sales preserve bird population;
- egg collection/sales preserve sellable egg balances;
- sales and expenses preserve revenue, cost, and profit calculations;
- audit records are created where required.

## Delivery rule

Ship this in small pull requests. Each pull request must state exactly which modules are fully supported, list automated test results, and include manual test steps. Do not claim CSV import complete while any advertised module is only a template or preview.


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
