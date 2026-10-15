# Frontend navigation refactor

## Top-level pages

1. Dashboard: farm metrics, attention items, and recent audit activity.
2. Flocks: batch dashboard, batches, houses/pens, and bird-cost controls.
3. Daily Work: daily records, mortality, culling, and water.
4. Feed & Stock: feed definitions, purchases, usage, and non-feed inventory.
5. Health: health records, drugs, and vaccinations.
6. Production: layer egg workflows and broiler weight/growth and sales workflows.
7. Sales & Money: customers, the common sales ledger, and expenses.
8. Reports: farm and batch reports and exports.
9. Settings: farm profile, farm defaults, configuration, account, backup, and pricing settings.

## Routing behavior

- The current page and subpage are stored in the URL hash, for example `#daily-work/mortality` or `#feed-stock/suppliers`.
- Back/forward and direct hash navigation are supported.
- The saved start page is applied after application settings load, unless the app is opened with an explicit route hash.
- Daily Work has separate Daily record, Mortality, Culling, and Water tabs.
- Health has separate Health records, Drugs & treatments, and Vaccination tabs.
- Feed & Stock has separate Feed & usage, Purchases, Other stock, and Suppliers tabs.
- Supplier records are loaded and managed through the existing supplier API; feed purchases can reference a registered supplier.
- The connection indicator probes the backend health endpoint and refreshes periodically. It reports healthy status only when the backend responds successfully and reports a valid database schema.
- The API base can be configured with `VITE_API_BASE_URL` or the desktop bridge's `getApiBaseUrl`; the local loopback address is only the development fallback.
- Navigation is fully local and adds no package dependency.
- Only the active page/tab sections are mounted. The backend remains the source of truth for farm calculations and records.

The nine page components live under `app/renderer/src/pages/`. Shared form state and API handlers remain in `App.jsx` and are exposed through `FarmManagerContext`; future cleanup can move domain-specific state/handlers into focused hooks.


## Farm-worker form usability

- Money fields are entered in naira (₦), not kobo. The renderer parses the input string into integer kobo before sending the existing backend API payload; no floating-point money arithmetic is used.
- Fields use persistent visible labels instead of relying on placeholders that disappear during typing.
- Batch creation and feed purchases select registered suppliers by name instead of asking operators to type database IDs.
- Tables, table headings, navigation labels, and keyboard shortcut hints use readable text sizes intended for the 1366×768 desktop target.
- The shell name and initials are derived from the saved farm profile.
