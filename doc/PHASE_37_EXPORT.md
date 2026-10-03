# Phase 37: Export

## Goal

Export the existing farm and batch reports without introducing a second calculation path.

Exports are serialization only:

`ReportService -> report map -> ReportExportService -> CSV/PDF`

The export layer does not calculate population, mortality, feed, costs, revenue, profit, production, health, sales, or inventory.

## CSV

Farm:
- GET /api/v1/reports/farm.csv

Batch:
- GET /api/v1/reports/batches/{id}.csv

CSV uses a deterministic flattened structure:

`path,value`

Nested objects use dot paths, and list entries use zero-based indexes.

Examples:

`dashboard.totalBirds,5000`
`dashboard.profitMinor,300000`
`sales[0].saleDate,2026-10-03`

The CSV includes the complete report tree rather than only dashboard, sales, and expense records.

UTF-8 output includes a BOM for spreadsheet compatibility.

## PDF

Farm:
- GET /api/v1/reports/farm.pdf

Batch:
- GET /api/v1/reports/batches/{id}.pdf

PDF generation uses Apache PDFBox.

The PDF is intentionally plain. It contains the complete flattened report values as path/value rows with automatic page breaks and line wrapping.

The purpose of Phase 37 is correct, portable export, not visual design.

## Frontend

The Reports section provides:

- Farm Export CSV
- Farm Export PDF
- Batch Export CSV
- Batch Export PDF

Downloads are created from backend-generated export data.

The batch export uses the currently selected batch.

## Date behavior

CSV and PDF accept the same optional `asOf` parameter as the JSON report endpoints.

Examples:

`/api/v1/reports/farm.pdf?asOf=2026-10-03`

`/api/v1/reports/batches/{id}.csv?asOf=2026-10-03`

No export-specific date filtering is performed in the renderer.

## Tests

`ReportExportServiceTest` verifies:

- complete nested report data is flattened into CSV
- DTOs are serialized as their fields instead of Java `toString()`
- CSV contains the expected calculated values
- PDF output is a real PDF document

The frontend API test verifies PDF responses are consumed as blobs.

Focused backend test:

    mvnw.cmd -f backend/pom.xml -Dtest=ReportExportServiceTest,ReportServiceTest test

Frontend tests:

    npm test

Full backend suite:

    npm run backend:test

Tests have not been claimed as passing until the commands are actually run.
