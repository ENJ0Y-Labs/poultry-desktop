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
| `LOW_FEED_STOCK` | Remaining feed (purchases + dated opening balances - usage) covers 3 days or less at the recent 7-day usage rate |
| `INSUFFICIENT_INVENTORY` | Active inventory item has zero or negative calculated stock |
| `LOW_INVENTORY` | Active inventory item is at or below its configured reorder level |
| `HIGH_MORTALITY` | Active batch cumulative mortality is at least 5% of initial birds |
| `MORTALITY_INCREASE` | Mortality in the latest 7 days is at least 2% of initial birds; critical at 5% |
| `UNUSUAL_FEED_CONSUMPTION` | A batch/feed type used at least 25% more in the latest 7 days than the preceding 7 days; critical at 50% |
| `DECLINING_WEIGHT` | Latest meat-bird average weight is at least 10% below its preceding recorded measurement, with the latest measurement no older than 7 days; critical at 20% |
| `RISING_EXPENSES` | Farm expenses are at least 25% above the preceding 7 days; critical at 50% |
| `FALLING_REVENUE` | Farm sales revenue is at least 25% below the preceding 7 days; critical at 50% |
| `MISSING_DAILY_RECORD` | Active batch has no daily record for the requested `asOf` date |
| `VACCINATION_DUE` | Active batch is at least 14 days old and has no vaccination in the previous 28 days |
| `UNUSUAL_PRODUCTION_DROP` | Layer total egg production (good + cracked) is down at least 20% versus the previous 7-day period |
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

Every returned insight includes `detectedAt`, `affectedMetric`, `ruleId`, `ruleDescription`, `thresholdContext`, and `suggestedAction`. Batch insights also include the current batch house ID/name/code where available. Farm-level financial insights and stock-item insights are explicitly attributed to their farm metric or stock item rather than inventing a batch.

The renderer shows this rule context and action. Clicking an insight opens the existing relevant module with allow-listed batch/feed/item/date context. The list is recomputed from current source records on every request, so resolved conditions disappear and there is no separately persisted stale-alert table. Rule queries are grouped by source entity and type to avoid duplicate cards.

The dashboard does not duplicate these formulas.

The farm dashboard's backend `attention` count also uses `AttentionService`, so the count and the displayed attention list come from the same rule engine.

## Important domain assumptions

Some attention concepts need policy values that are not yet stored as farm configuration.

The current defaults are deliberately conservative and centralized in `AttentionService`:

- cumulative high mortality: 5% warning, 10% critical; recent 7-day mortality: 2% warning, 5% critical
- unusual feed usage: 25% increase warning, 50% critical
- declining meat-bird weight: 10% drop warning, 20% critical
- rising expenses / falling revenue: 25% adverse movement warning, 50% critical
- feed cover warning: 3 days
- vaccination interval: 28 days
- minimum vaccination-age check: 14 days
- broiler target sale age: 42 days
- sale warning: 35 days
- backup overdue: 7 days\n- backup overdue is evaluated only when automatic backups are enabled, using the configured automatic backup directory

FCR is intentionally not classified as poor by a hard-coded threshold. The backend calculates FCR, but a meaningful alert needs a farm-configured or approved bird-type/age benchmark. Until that policy exists, no poor-FCR insight is emitted. Financial trends require a non-zero previous-period baseline; incomplete history does not trigger an alert.

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
