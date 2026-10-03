# Phase 36: Reports

## Goal

Farm and batch reports expose the same calculated values used by the dashboards.

The report layer does not reimplement population, feed, production, cost, revenue, profit, or growth formulas.

## Farm report

The backend report contains:
- Farm summary
- Bird population by batch
- Mortality total and by batch
- Feed inventory/cost data
- Expenses through the selected as-of date
- Revenue
- Profit
- Egg production for layer batches
- Broiler growth for broiler batches
- Sales through the selected as-of date
- Inventory items and movements through the selected as-of date

The farm report obtains headline totals directly from DashboardService.farm(asOf).

Per-batch population uses BirdPopulationService.
Feed uses FeedService.
Egg production uses EggManagementService.
Broiler growth uses BroilerProductionService.
Expenses use ExpenseService.
Sales use SalesService.
Inventory uses InventoryService.

## Batch report

The backend report contains:
- Batch summary
- Population
- Mortality
- Feed
- Costs
- Revenue
- Profit
- Production
- Health
- Sales

Batch reports use DashboardService.batch(asOf) for the same dashboard metrics.

Additional report sections delegate to:
- BirdPopulationService
- BirdCostService
- FeedService through the dashboard feed result
- EggManagementService
- BroilerProductionService
- HealthManagementService
- SalesService

Layer production includes egg inventory, egg quality, collections and egg sales.

Broiler production includes growth, weights and broiler sales.

## As-of behavior

All calculated report values accept an optional asOf date.

Historical records are excluded when their business date is after the requested date.

Health and expense services expose as-of overloads so reports do not need to query those tables directly.

## API

Existing report endpoints remain:
- GET /api/v1/reports/farm
- GET /api/v1/reports/batches/{id}
- GET /api/v1/reports/farm.csv
- GET /api/v1/reports/batches/{id}.csv

The JSON endpoints now expose the complete farm/batch report sections.

## Frontend

The Reports area now provides:
- Farm report summary
- Batch report selector
- Batch report metrics
- Layer production details
- Broiler growth details
- Health record counts
- CSV export for farm and batch reports

The frontend consumes report API responses only. It does not calculate report business metrics.

## Tests

ReportServiceTest verifies that reports delegate to the same dashboard/domain services rather than duplicating formulas or querying report-specific calculations directly.

Run:

    npm run backend:test

Focused:

    mvnw.cmd -f backend/pom.xml -Dtest=ReportServiceTest test

Tests have not been claimed as passing until the commands are actually run.