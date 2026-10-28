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

## Historical health, drug, and vaccination templates (v1.0)

Import the batch opening record first. Every row references an existing batch through its exact human-readable `batch_code`, and the backend rejects batches that are not ACTIVE or dates before the batch placement date. The preview is read-only; confirmation revalidates and writes through the existing health-management service in one transaction.

### Health / treatment records

Headers: `batch_code,record_date,condition_problem,description,action`.

All fields are required. The current domain stores health/treatment history as one health record with a condition/problem, description, and action. There is no separate treatment record type or controlled list of health-condition values in the current model; values remain text, matching manual entry.

### Drug records

Headers: `batch_code,record_date,drug,quantity,cost_minor,reason`.

All fields are required. `quantity` is a positive whole number in the existing model's quantity units. `cost_minor` is a positive whole number of currency minor units, not naira formatted as a decimal. Importing a drug record uses the existing service, which also creates the normal DRUGS expense and audit entry. The current drug record model stores a drug name as text and does not reference an inventory item ID or consume stock. Do not interpret the drug name as an inventory code; no inventory linkage is invented by this import.

### Vaccination records

Headers: `batch_code,record_date,vaccine,dose,quantity,notes`.

`batch_code`, `record_date`, `vaccine`, `dose`, and `quantity` are required; `notes` is optional. `quantity` is a positive whole number in the current model's quantity units. The dose remains text because the existing model has no structured dose-unit field or vaccine-type enum. Vaccine names and dose values are not linked to inventory item IDs in the current model.

For all three modules, exact retries of the same batch/date and complete record details are skipped. Different health records on the same date are allowed. No existing record is silently updated. If any row fails validation or any confirmed write fails, the complete import transaction rolls back, including the existing service's expense and audit writes. Feed remains in its separate subsystem.


## Historical feed purchase and usage templates

Feed history imports are separate modules and must be previewed before confirmation.

| Module | Template version | Fields | Notes |
|---|---|---|---|
| Historical feed purchases | 1.0 | `feed_type_name, supplier_name, purchase_date, quantity, unit, total_cost_naira` | `supplier_name` is optional. Purchase cost is entered in naira with at most two decimal places and converted by the backend to integer kobo. |
| Historical feed usage | 1.0 | `feed_type_name, batch_code, usage_date, quantity, unit, reason` | `reason` is optional; batch code must resolve to an existing active batch. |

Use the exact configured feed type name and, when supplied, the exact active supplier name. The current feed-type and supplier models do not expose dedicated stable codes, so the template must not pretend that they do. Batch usage uses the existing human-readable batch code. Internal database IDs are not accepted in the CSV.

Dates use `YYYY-MM-DD`. Quantity must be positive and have at most three decimal places, matching the existing feed service. The unit must exactly match the configured feed type unit. The importer never assumes that a bag has a particular weight. If `kg_per_unit` is unknown, it remains unknown; stock quantities and FIFO costing continue in the configured purchase unit, and kilogram-based calculations remain unavailable until the operator configures a conversion.

The backend converts naira using decimal arithmetic and rejects amounts with more than two decimal places or values outside the integer-kobo range. It does not use floating-point money.

Preview checks the full file without writing, resolves references, checks the batch placement date and feed-type applicability, detects exact repeated/existing transactions, respects the existing service's backdating restrictions, and simulates FIFO availability at each usage date. A later purchase cannot make an earlier usage valid. On a shared date, purchases are processed before usage so same-day stock can be consumed without changing the existing date-only feed model.

Confirmed imports are sorted by date, with purchases before usage on the same date, and call the existing `FeedService` methods inside one transaction. This preserves purchase expenses, FIFO allocation, batch additional-cost events, and audit records. Feed usage does not create a second feed expense. Any write failure must roll back the entire import.

Exact purchase duplicates are identified by feed type, supplier, date, quantity, and total cost. Exact usage duplicates are identified by batch, feed type, date, quantity, and reason. These semantics prevent a retry from duplicating identical imported rows; they also mean two genuinely separate transactions with identical fields are indistinguishable in the current schema. Review the preview carefully when that situation occurs.

## Feed import verification

Backend tests cover exact naira-to-kobo conversion, purchase retry behavior, FIFO costing across multiple purchase lots, unknown kilogram conversions, insufficient dated stock, and transaction rollback of purchase, expense, and audit writes. Before release, also verify in a disposable farm database that feed inventory, batch feed costs, and reports agree with manually entered equivalent records.
