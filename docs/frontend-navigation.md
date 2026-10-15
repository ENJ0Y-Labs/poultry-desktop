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

- The current page is stored in the URL hash, for example `#daily-work`.
- Back/forward and direct hash navigation are supported.
- Existing saved start-page values and legacy section names are mapped to their new page.
- Navigation is fully local and adds no package dependency.
- Only active-page sections are mounted. The backend remains the source of truth for farm calculations and records.

## Known follow-up

Some grouped areas currently present their existing subsections as cards within the page rather than separate tab panels. The nine page components now live under `app/renderer/src/pages/`. Shared form state and API handlers remain in `App.jsx` and are exposed through `FarmManagerContext`; a future cleanup can move domain-specific state/handlers into focused hooks. Daily Work and Health still keep related operations within their existing card sections rather than splitting every operation into its own tab.
