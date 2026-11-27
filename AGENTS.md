

## Existing farm records, calculations, and offline operation (Issue #302)

- Preserve the existing SQLite farm database and the pinned packaged user-data directory `%APPDATA%/Poultry Farm Manager` across upgrades and product branding changes. Never silently initialize a replacement empty farm when an existing database is present.
- Keep existing domain services and persisted source records authoritative for population, feed FIFO costing, production, sales, expenses, dashboard totals, and reports. Review workflows must not create a parallel operational ledger or duplicate calculations.
- Normal farm operation must remain local-first and usable without internet access. Do not introduce remote runtime dependencies, CDN assets, telemetry, cloud APIs, or internet-only authentication into the normal operation path without explicit approval.
- Run the isolated `MockFarmAcceptanceIntegrationTest` and full backend/frontend/build gates for changes touching existing farm workflows. Its fixture must remain isolated from real farm data.
- A CI pass does not prove Windows upgrade continuity or offline desktop behaviour. Follow `docs/issue-302-existing-farm-regression.md` on a disposable Windows profile before release; record the actual app/schema versions and outcome.
