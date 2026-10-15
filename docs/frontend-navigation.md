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
- The backend connection indicator probes the local API and refreshes periodically. It reports API reachability, not a guarantee that every database operation will succeed.
- The API base can be configured with `VITE_API_BASE_URL` or the desktop bridge's `apiBaseUrl`; the local loopback address is only the development fallback.
- Navigation is fully local and adds no package dependency.
- Only the active page/tab sections are mounted. The backend remains the source of truth for farm calculations and records.

The nine page components live under `app/renderer/src/pages/`. Shared form state and API handlers remain in `App.jsx` and are exposed through `FarmManagerContext`; future cleanup can move domain-specific state/handlers into focused hooks.
