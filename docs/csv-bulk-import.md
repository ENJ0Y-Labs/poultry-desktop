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
