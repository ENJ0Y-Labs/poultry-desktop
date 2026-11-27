# Issue #302: existing farm regression acceptance

## Contract

The Manager Review Centre and its supporting workflow must not change the behaviour or availability of the existing local farm system.

The following remain non-negotiable:

- Existing farm data stays in the same SQLite database. Do not create a second operational database for submissions, reviews, or notifications.
- Packaged upgrades continue using `%APPDATA%/Poultry Farm Manager` as the pinned Electron user-data directory. A product rename or UI refresh must not make an existing database appear missing.
- Existing domain services remain authoritative for bird population, feed FIFO costing, egg inventory, sales, expenses, health, and reports. Review workflows may call those services only through their typed adapters.
- Derived totals are recalculated from persisted source records. Do not replace existing formulas with review-specific calculations or cache stale totals.
- The application remains usable with no internet connection. Normal farm operations, authentication, local API calls, SQLite persistence, reporting, and local backup/restore must not depend on a remote service, CDN, telemetry endpoint, or cloud API.
- Worker report drafts may be stored locally for recovery, but a report submission is not an official operational record until the Manager merge transaction succeeds.

## Automated regression coverage

The existing `MockFarmAcceptanceIntegrationTest.seededLayerAndBroilerScenarioReconcilesOperationsInventorySalesAndReports` is the backend cross-module baseline. It uses an isolated in-memory SQLite database and checks:

- Layer and Broiler population totals after mortality.
- Feed purchase and usage balance.
- Egg collection and sale totals.
- Customer, sales, and expense persistence.
- Dashboard and farm-report reconciliation.

Run it from the repository root:

```powershell
.mvnw.cmd -f backend\pom.xml test -Dtest=MockFarmAcceptanceIntegrationTest
```

Then run all automated gates:

```powershell
npm run lint
npm test
.mvnw.cmd -f backend\pom.xml test
.mvnw.cmd -f backend\pom.xml verify
npm run build
```

All test fixtures must use in-memory or uniquely created temporary databases. Never use the live farm database, AppData, or a real backup as a test fixture.

## Disposable Windows upgrade and offline acceptance

CI cannot prove that an installed Windows application still opens the same user's existing database. Perform this checklist against a copied disposable profile, never the live farm profile:

1. Install or launch the previous release in a disposable Windows user-data directory.
2. Create a farm with one Layer batch and one Broiler batch. Record the farm ID, batch IDs, opening populations, feed stock, egg stock, sales, expenses, and a report total.
3. Close the application cleanly and copy the entire disposable user-data directory as the recovery point.
4. Install/launch the candidate release without deleting or renaming the existing user-data directory.
5. Confirm the app opens the same farm and existing records. Compare the saved IDs and totals with the pre-upgrade record.
6. Recalculate the dashboard and farm reports. Confirm values still reconcile with the source records and no duplicate farm, batch, or ledger rows were created by startup/migration.
7. Disable network connectivity before launching the app. Confirm sign-in/session handling, farm navigation, daily records, feed, production, sales, expenses, and reports work through the local backend.
8. While still offline, create a new operational record, restart the app, and confirm the record persists.
9. Confirm no required screen is blocked by a remote font, CDN asset, analytics call, cloud API, or internet-only authentication dependency.
10. Restore the copied disposable profile if the candidate fails. Record Windows version, old/new application versions, schema version, and the exact failure.

Do not claim the manual upgrade/offline checks passed based on CI. Record the actual test environment and outcome.

## Failure conditions

Treat any of the following as a regression:

- Existing farms or records disappear after upgrade.
- A new empty farm/database is silently created instead of opening the existing one.
- Any persisted source record changes or duplicates without an explicit user operation.
- Dashboard/report totals differ from the authoritative records.
- A farm operation requires internet access during normal local operation.
- Review submission, assignment, checks, notifications, or merge writes a second operational ledger or bypasses the owning domain service.
- An unavailable internet connection causes the app to fail startup when the local backend and database are healthy.
