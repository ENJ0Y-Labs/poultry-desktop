# Phase 38: Alerts / Attention System

## Goal

Create one backend-derived attention system for operational problems that deserve action.

The renderer does not calculate alert conditions. It calls:

`GET /api/v1/attention?asOf=YYYY-MM-DD`

and renders the returned attention rows.

## Rules

The backend currently evaluates:

| Type | Rule |
| --- | --- |
| `LOW_FEED_STOCK` | Remaining feed covers 3 days or less at the recent 7-day usage rate |
| `INSUFFICIENT_INVENTORY` | Active inventory item has zero or negative calculated stock |
| `LOW_INVENTORY` | Active inventory item is at or below its configured reorder level |
| `HIGH_MORTALITY` | Active batch mortality is at least 5% of initial birds |
| `MISSING_DAILY_RECORD` | Active batch has no daily record for the requested `asOf` date |
| `VACCINATION_DUE` | Active batch is at least 14 days old and has no vaccination in the previous 28 days |
| `UNUSUAL_PRODUCTION_DROP` | Layer egg production is down at least 20% versus the previous 7-day period |
| `BATCH_NEARING_SALE` | Active broiler reaches 35 days; 42 days is the target sale age |
| `BACKUP_OVERDUE` | Automatic backups are enabled and no automatic database backup exists, or the latest one is at least 7 days old |

Severity is backend-derived as well:

- `CRITICAL`: immediate operational risk
- `WARNING`: action should be scheduled

High mortality becomes critical at 10%, production drop becomes critical at 30%, and a broiler batch becomes critical at 49 days.

## Architecture

```text
SQLite data
    ↓
AttentionService
    ↓
GET /api/v1/attention
    ↓
React dashboard
```

The dashboard does not duplicate these formulas.

The farm dashboard's backend `attention` count also uses `AttentionService`, so the count and the displayed attention list come from the same rule engine.

## Important domain assumptions

Some attention concepts need policy values that are not yet stored as farm configuration.

The current defaults are deliberately conservative and centralized in `AttentionService`:

- high mortality: 5%
- feed cover warning: 3 days
- vaccination interval: 28 days
- minimum vaccination-age check: 14 days
- broiler target sale age: 42 days
- sale warning: 35 days
- backup overdue: 7 days\n- backup overdue is evaluated only when automatic backups are enabled, using the configured automatic backup directory

These should become farm-configurable only when the product has a real configuration workflow and the farm requirements justify it. Do not scatter these numbers through the React application.

## Tests

Backend integration coverage:

`AttentionServiceIntegrationTest`

It verifies that real SQLite/Flyway data produces the expected attention types and that the dashboard count uses the same attention service.

Frontend coverage:

- `api.test.js` verifies the attention API client.
- `App.test.jsx` verifies backend-derived attention rows are rendered.

Focused backend test:

```powershell
mvnw.cmd -f backend\pom.xml -Dtest=AttentionServiceIntegrationTest test
```

Frontend:

```powershell
npm test
```

Full backend:

```powershell
npm run backend:test
```

Tests must be run locally before claiming Phase 38 is verified.
