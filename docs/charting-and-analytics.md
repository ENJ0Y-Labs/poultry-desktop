# Charting and analytics

Charts are presentation views over existing Spring Boot responses. They do not write calculated values or replace backend calculations. Keep date boundaries inclusive and preserve the meaning and units of each source field.

## Current chart placement

- **Dashboard:** daily sales and expenses over a selectable 7, 30, 90 day, or custom period, plus a current live-bird distribution by poultry type, house, or batch. The dashboard distribution uses the dashboard's current population totals; period activity comes from the farm report endpoint.
- **Flocks:** grouped batch population outcomes and a horizontal current-bird comparison by house. Keep event series grouped rather than stacked: transfers and physical count adjustments mean mortality, culling, sales, and current birds do not form a simple composition.
- **Daily work:** cumulative feed by one selected batch and feed type, plus daily mortality for explicitly selected batches. Never combine feed quantities across units. Date requests are limited to 366 days and at most eight batches are shown for mortality comparison.
- **Production:** the backend production trend remains authoritative for HDP and meat-bird average weight. Additional charts compare good/cracked eggs, and show meat-bird age versus recorded average weights. The weight-band view counts records by their recorded average; individual bird weights are not stored. Egg size and grade charts are not supported by the current schema.
- **Feed & Stock:** current stock is compared only within one selected unit. Movement charts derive historical on-hand balances from the selected item's movement ledger and current balance; receipts, issues, and waste remain distinct.
- **Sales & Money / Reports:** daily sales and expense comparisons, expense category share, and revenue less expenses. The latter is explicitly not labeled profit because the daily chart input does not allocate bird costs. House or batch filtering excludes farm-level expenses, which have no batch/house attribution.

## Data and presentation rules

- Fetch only existing report, daily-record, inventory-movement, egg, and weight data through the renderer API client. Do not create demo series or synthetic zero days.
- Preserve missing event dates as gaps where a line represents recorded events. A blank series uses the shared empty-state component.
- Aggregate integer minor units for chart values, and format currency through the application's money formatter. Never persist chart totals.
- Keep filters contextual to the chart and show the active date window. Keep unlike units in separate series or provide an explicit unit selector.
- Use the shared chart panel, tooltip styling, and responsive grid. Ensure charts fit narrow windows and have readable labels and legends.
- If the chart requires data the current API/schema does not provide, omit it or clearly describe the available proxy. Do not infer egg grades, per-bird weight distributions, or attributable profit from aggregate data.

## Backend query notes

The current implementation reuses bounded report and daily-record requests, production trend endpoints, and the inventory movement ledger. Farm report date-range responses provide dated sales and expenses plus current population snapshots. Daily records provide dated mortality and feed use. Production trend endpoints preserve historical population/growth calculations. Inventory movements are append-only and the current stock balance comes from the inventory service. If a future chart needs a farm-wide daily aggregate, add a bounded service/API query rather than fetching every unbounded collection into the renderer.
