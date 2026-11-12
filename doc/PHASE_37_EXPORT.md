# Phase 37: Export

## Goal

Export the existing farm and batch reports without introducing a second calculation path.

Exports are serialization only:

`ReportService -> report map -> ReportExportService -> CSV/PDF`

The export layer does not calculate population, mortality, feed, costs, revenue, profit, production, health, sales, or inventory. Report values remain owned by the same backend services used by the dashboards.

## CSV

Farm:
- GET /api/v1/reports/farm.csv

Batch:
- GET /api/v1/reports/batches/{id}.csv

CSV uses a stable, documented column order:

`farm_name,report_title,batch_code,batch_type,house_id,generated_at,selected_period,currency,as_of,report_type,section,record,field,value,unit`

Each row repeats the report metadata so spreadsheet filtering and sorting do not detach values from their report context. Batch reports also identify the selected batch code, bird type, and house ID. Nested report objects are flattened into section/record/field/value/unit rows. Arrays use a human-readable record identifier where available and a stable one-based fallback label otherwise.

UTF-8 output includes a BOM for spreadsheet compatibility. Text cells are quoted and escaped. Formula-like text values are prefixed to prevent spreadsheet formula execution.

## PDF

Farm:
- GET /api/v1/reports/farm.pdf

Batch:
- GET /api/v1/reports/batches/{id}.pdf

PDF generation uses Apache PDFBox. Exports include farm name, report title, generation timestamp, selected as-of period, configured currency, readable column headings, row separators, line wrapping and automatic page breaks. Table headings and report context repeat on continuation pages.

A supported local Unicode font is required so currency symbols and farm data remain readable without downloading fonts or relying on a network service.

## Frontend

The Reports section provides farm and batch CSV/PDF downloads.

- Both report types use the selected as-of date.
- The batch selector can be narrowed by house before selecting the batch to export.
- Export actions show a preparing state, prevent duplicate export clicks, and report success only after the download response has been received.
- Failed and timed-out exports show an actionable error. Export requests allow up to 120 seconds for larger local reports.
- Download object URLs are revoked after the browser has had time to start the download.

## Date, currency and scope behavior

- Report JSON values and stored/API values remain unchanged.
- Export date labels follow the farm's configured application date format.
- Monetary minor-unit values are formatted using the farm's configured ISO currency. The integer minor-unit source value is not changed.
- The selected as-of date is included in both CSV and PDF metadata and is not replaced with the current date.
- Farm reports export the whole farm. Batch reports export only the selected batch. A house filter narrows the selectable batch list; it does not silently convert a batch report into a farm report.
- Backend report services remain the only source of calculations and report totals.

## Manual mock-data verification

Use a disposable test farm or a fresh local database. Do not run these checks against the live Grantino Farms records.

1. Configure the test farm with a farm name, NGN currency, Africa/Lagos timezone, and application date format `DD/MM/YYYY`.
2. Create two houses and at least three batches across them: one LAYER batch and two meat-bird batches. Add daily records, mortality/culling events, egg collections, feed purchases and usage, inventory movements, sales, expenses, and production weights.
3. Open Reports and select a historical as-of date. Confirm the preview updates to that date without changing stored records.
4. Export the farm report as CSV and PDF. Check farm name, report title, generation timestamp, selected period, NGN currency, feed purchased/used/remaining, population, mortality, egg and growth details, inventory, sales, expenses, revenue, and profit against the backend report preview.
5. In Batch reports, choose House One. Confirm the batch selector only lists batches assigned to House One. Select one batch, refresh, and export CSV/PDF. Confirm batch code/type/house metadata and that no other batch's records appear.
6. Change the house while a batch from the previous house is selected. Confirm the stale batch report disappears and export buttons remain disabled until a batch in the new house is selected.
7. Select an empty batch or a report date before its first records. Confirm the export still contains report metadata and empty sections are labelled rather than omitted or misrepresented as zero.
8. Use a disposable dataset with at least 150 sales/operations records. Export PDF and confirm page breaks repeat report context and table headings. Confirm the renderer remains interactive while the Preparing state is visible.
9. Stop the local backend or simulate a failed export response. Confirm the UI shows an actionable error, does not show success, and remains navigable. Restore the backend and retry.
10. Repeat the filter flow using only the keyboard (Tab, arrow keys, Enter, Escape where applicable). Check the Reports page at 1024×768, 1280×800, and 1920×1080 window sizes.

## Coverage

`ReportExportServiceTest` verifies:

- stable CSV headers and report metadata
- nested report data and empty sections
- CSV escaping for formula-like values
- configured currency and date formatting
- PDF title, farm name, generation time, selected period, currency, headings and output validity

`ReportServiceTest` verifies that report metadata is sourced from farm and application settings while calculations continue to delegate to existing domain services.

Focused backend test:

    mvnw.cmd -f backend/pom.xml -Dtest=ReportExportServiceTest,ReportServiceTest test

Frontend tests:

    npm test

Full verification:

    npm test
    npm run lint
    npm run backend:verify
    npm run build

Tests have not been claimed as passing until the commands are actually run.
