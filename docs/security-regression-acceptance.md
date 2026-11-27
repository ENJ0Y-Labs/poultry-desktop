# Security, regression, and mock-data acceptance testing

This phase adds repeatable backend HTTP security checks and a cross-module synthetic farm acceptance scenario. All automated tests use isolated SQLite in-memory databases and disposable Spring application contexts. They must never point at `data/poultry.db`, a user's AppData database, a backup directory, or a real farm database.

## Automated gates

Run from the repository root:

```powershell
npm ci
npm run lint
npm test
```

Run backend checks from the repository root:

```powershell
.mvnw.cmd -f backend\pom.xml test
.mvnw.cmd -f backend\pom.xml verify
```

On macOS/Linux use `bash ./mvnw -f backend/pom.xml test` and `bash ./mvnw -f backend/pom.xml verify`.

Focused tests:

```powershell
.mvnw.cmd -f backend\pom.xml test -Dtest=SecurityAcceptanceIntegrationTest
.mvnw.cmd -f backend\pom.xml test -Dtest=MockFarmAcceptanceIntegrationTest
```

CI runs the frontend lint, full Vitest suite, production renderer build, backend compile, full backend test suite, Maven verification, and Windows Electron packaging. A green CI run proves automated checks only; it does not prove a human completed the desktop acceptance checklist below.

## Security acceptance coverage

`SecurityAcceptanceIntegrationTest` exercises the real Spring MVC request pipeline with authentication required:

- Anonymous protected reads are rejected.
- State-changing requests without the `X-Requested-With: XMLHttpRequest` marker are rejected before a write occurs.
- A marked but unauthenticated write is still rejected.
- Workers can use their own submission endpoints but cannot access farm-wide operations or Manager review.
- Administrators cannot use Manager review APIs; Managers can.
- A role change is resolved from the database on the next request using the existing session, and a suspended user loses access.
- Credentialed CORS preflight is allowed only for an explicitly listed renderer origin; an attacker-controlled origin is rejected.

The role tests deliberately call the HTTP endpoints. Renderer navigation visibility is not considered an authorization test.

## Synthetic farm acceptance scenario

`MockFarmAcceptanceIntegrationTest` creates a disposable farm with Layer and Broiler houses/batches, feed purchase and usage, mortality, daily operations, egg collection and sale, a customer, and an expense. It then checks that dashboard totals and farm reports reconcile with the inserted records.

Fixture values are explicitly synthetic:
- 500 Layer birds and 100 Broiler birds placed on 2026-11-01.
- 3 Broiler mortalities on 2026-11-03.
- 10 kg of mock Broiler feed purchased and 2 kg used.
- 300 good eggs and 5 cracked eggs collected; one 30-egg crate sold.
- A separate NGN 750.00 batch expense.

The test database is named in-memory and the Spring context is discarded after each test. Do not replace this with a persistent database path or add fixture records to a production database.

## Manual desktop acceptance checklist

Run this checklist only in a disposable Windows profile/database created for testing:

1. Create a fresh test farm and separate WORKER, MANAGER, and ADMINISTRATOR accounts.
2. Confirm an anonymous session cannot open protected farm data and that sign-in creates an HTTP-only session cookie.
3. Sign in as WORKER. Submit a report, attach a supported evidence file, and verify the attachment is shown only for the correct immutable version. Confirm the Worker cannot open the Manager queue or direct ledger pages.
4. Sign in as MANAGER. Review the Worker report, inspect its evidence, request changes, and verify the Worker can edit only the returned version. Re-submit and complete a valid review/merge.
5. Sign in as ADMINISTRATOR. Verify account administration is available but Manager review and merge actions remain denied.
6. Enter mock Layer and Broiler daily records, mortality, feed purchase/usage, egg collection/sale, bird sale, and expenses. Reconcile the dashboard and farm/batch reports against the known entries.
7. Attempt invalid quantities, incompatible batch/feed types, future dates, duplicate submissions, overselling, and stale review data. Confirm rejected operations leave all official ledgers unchanged.
8. Create a backup, validate it, and restore it into the disposable test profile. Verify the expected records and evidence remain available.
9. Repeat a restore failure/rollback drill on Windows with a second disposable database. Verify the previous database is recovered and the application starts normally.
10. Remove the disposable profile only after retaining any test logs needed for a defect report.

Record manual outcomes, OS version, desktop release version, database schema version, and test date in the release checklist. Never mark a manual item complete based only on CI.

## Regression policy

- Do not weaken or delete an existing test to make a new test pass.
- Keep expected values explicit and deterministic; avoid relying on the current date, locale, host timezone, or live farm data.
- Add a regression test for each fixed defect at the owning layer (renderer, Electron main process, REST boundary, service/domain, or SQLite transaction).
- For authorization changes, test both allowed and denied roles at the HTTP boundary.
- For financial or population workflows, assert both the returned result and the persisted ledger/reconciliation state.
- For backup/restore, use temporary database files and verify both successful restore and failure recovery.
