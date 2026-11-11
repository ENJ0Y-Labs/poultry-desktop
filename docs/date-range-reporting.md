# Reporting date ranges

## Shared control and state

The farm dashboard and farm/batch reports share one renderer date-range state. The same range is passed to compatible dashboard requests, report previews, report exports, audit activity, and production trend charts. Changing a range never writes to farm records.

The default is **Last 30 days**. Supported presets are Today, Yesterday, Last 7 days, Last 30 days, Last 90 days, This month, Last month, This year, and Custom range. Every preset uses inclusive calendar-date boundaries.

Custom ranges require both dates and from <= to. An invalid range is shown inline, clears the affected period views, and is not sent to the API. The active range is shown in the control and report/chart context.

## Farm-local calendar rules

Preset boundaries are derived from the farm's IANA timezone, not the computer's timezone. Date-only values remain YYYY-MM-DD calendar dates through the API; they are not parsed as UTC instants in the renderer.

The active application date-format preference is used for the visible range summary. The native date inputs remain keyboard-operable.

Audit timestamps are instants, so the backend converts the selected farm-local start date to the start of that day and the end date to the start of the following day. The audit query uses a half-open interval. SQLite julianday comparisons avoid lexical ordering errors caused by ISO timestamps with different fractional-second precision.

## What the range filters

The backend returns raw period aggregates and range-filtered records. The selected period applies to activity values such as mortality, good/cracked egg collections, sales, expenses, health/drug/vaccination records, production records, and audit activity.

React derives presentation-only period profit from the returned integer minor-unit revenue and expense totals. The derived value is not persisted. CSV/PDF export requests may pass that renderer-derived value to the export formatter so the exported period profit matches the screen; the backend formatter only serializes it.

Current/lifetime snapshots are not treated as period activity. Current live population, house occupancy, current feed stock, current egg inventory, batch-to-date feed cost, average weight, and FCR remain snapshot/batch-to-date values. The dashboard and report notes identify these distinctions. A historical activity range does not rewrite or rewind current balances.

Production trend endpoints retain their existing 366-day maximum. A custom range longer than 366 days still filters the other compatible dashboard/report data, while the production chart displays a clear range-limit message rather than issuing an invalid request.

## API contract

- GET /api/v1/dashboard?from=YYYY-MM-DD&to=YYYY-MM-DD
- GET /api/v1/reports/farm?from=YYYY-MM-DD&to=YYYY-MM-DD
- GET /api/v1/reports/batches/{id}?from=YYYY-MM-DD&to=YYYY-MM-DD
- GET /api/v1/audit?limit=20&from=YYYY-MM-DD&to=YYYY-MM-DD
- Farm and batch CSV/PDF endpoints accept the same from/to pair. Export endpoints also accept the renderer-derived periodProfitMinor for display only.

Both dates are required whenever either range parameter is supplied. The backend rejects missing or reversed boundaries. Existing asOf report and dashboard requests remain supported for callers that still need a single historical snapshot date.

## Verification

Focused automated checks (PowerShell):

    npm test -- app/renderer/src/utils/dateRange.test.js app/renderer/src/utils/reportMetrics.test.js app/renderer/src/components/DateRangeFilter.test.jsx app/renderer/src/components/ProductionTrendChart.test.jsx
    npm run backend:test -- -Dtest=ReportDateRangeTest,DashboardDateRangeIntegrationTest,ReportServiceTest,ReportExportServiceTest

Full repository checks:

    npm test
    npm run lint
    npm run backend:verify
    npm run build

## Manual mock-data test

Use a disposable test farm only. Never reset or seed a real farm database.

1. Set the farm timezone to Africa/Lagos and set the date format to each supported option in turn.
2. Open Dashboard and select Today, Yesterday, Last 7 days, Last 30 days, Last 90 days, This month, Last month, and This year. Confirm the visible boundaries use the farm date and the configured date format.
3. Select Custom range and set 2026-02-28 through 2026-03-01. Confirm both boundary dates are included in mortality, good/cracked eggs, revenue, expenses, sales, health, and audit results.
4. In the same test data, add activity on 2026-03-02. Confirm it is excluded from the selected period.
5. Confirm current live birds still reflect all events through the current farm date, not only events inside the selected range. Confirm house occupancy and feed stock remain current snapshots.
6. Open Farm reports and Batch reports. Confirm both use the same selected range, filtered record tables/counts, and current snapshot notes.
7. Confirm layer HDP and meat-bird weight charts request the shared range. For a custom range longer than 366 days, confirm only the trend chart shows its documented limit message.
8. Export farm and batch CSV/PDF. Confirm the range appears in export metadata/title and period profit matches the screen.
9. Set the custom start date later than the end date. Confirm the error is visible, affected views clear, and no request is sent until the range becomes valid.
10. Record database row counts before and after changing presets/custom dates. Confirm no farm record was inserted, updated, or deleted.
11. Repeat with a second farm timezone to confirm preset dates and audit-day boundaries follow that timezone.
12. At the supported 1100×650 and 1366×768 window sizes, keyboard through the preset selector and both date inputs. Confirm the control and error summary remain visible and usable.

Runtime and packaged-Electron checks must be recorded as not run unless actually executed.
