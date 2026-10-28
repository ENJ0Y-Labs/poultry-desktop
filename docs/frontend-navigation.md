# Frontend navigation refactor

## Top-level pages

1. Dashboard: farm metrics, attention items, and recent audit activity.
2. Flocks: batch dashboard, batches, houses/pens, and bird-cost controls.
3. Daily Work: daily records, feed usage, mortality, culling, and water.
4. Feed & Stock: feed definitions, purchases, remaining feed stock, suppliers, and non-feed inventory. Daily feed consumption is entered in Daily Work and still uses the authoritative FIFO feed ledger.
5. Health: health records, drugs, and vaccinations.
6. Production: layer egg workflows and broiler weight/growth and sales workflows.
7. Sales & Money: customers, the common sales ledger, and expenses.
8. Reports: farm and batch reports and exports.
9. Import / Export: supported customer/supplier CSV imports, planned historical batch import, and entry points to supported farm/batch report exports.\n10. Settings: farm profile, farm defaults, configuration, account, backup, and pricing settings.

## Routing behavior

- The current page and subpage are stored in the URL hash, for example `#daily-work/mortality` or `#feed-stock/suppliers`.
- Back/forward and direct hash navigation are supported.
- The saved start page is applied after application settings load, unless the app is opened with an explicit route hash. Import / Export is a routable destination but is intentionally not an available saved start page; the saved start-page allow-list remains aligned across Settings, backend validation, and the database constraint.
- Daily Work has separate Daily record, Mortality, Culling, and Water tabs. The Daily record tab uses one selected batch/date for notes and feed usage; feed usage remains a separate transaction in the authoritative FIFO feed ledger.
- Health has separate Health records, Drugs & treatments, and Vaccination tabs.
- Feed & Stock has separate Feed & usage, Purchases, Other stock, and Suppliers tabs. The Feed & usage tab manages feed types and stock visibility; daily consumption is recorded from Daily Work.
- Supplier records are loaded and managed through the existing supplier API; feed purchases can reference a registered supplier.
- The connection indicator probes the backend health endpoint and refreshes periodically. It reports healthy status only when the backend responds successfully and reports a valid database schema.
- The API base can be configured with `VITE_API_BASE_URL` or the desktop bridge's `getApiBaseUrl`; the local loopback address is only the development fallback.
- Navigation is fully local and adds no package dependency.
- Only the active page/tab sections are mounted. The backend remains the source of truth for farm calculations and records.

The top-level page components live under `app/renderer/src/pages/`. Shared form state and API handlers remain in `App.jsx` and are exposed through `FarmManagerContext`; future cleanup can move domain-specific state/handlers into focused hooks.


## Farm dashboard presentation
- The dashboard “Record feed usage” quick action opens Daily Work / Daily record and preselects the active batch, compatible feed type, and current farm-local date. Keep this aligned with the feed-usage form location.

- The farm dashboard labels its business date as “Dashboard as of” so it is not mistaken for the computer's current date or a date-range selector.
- The dashboard formats that date using the saved application date-format preference. Business dates are parsed as calendar components, not as UTC instants.
- Total birds, revenue, and profit are primary metrics. Other KPIs remain visible but use a quieter visual treatment.
- Feed and attention panels share the available dashboard width and stack at narrower supported widths; neither panel truncates its content to force equal heights.
- Dashboard currency values use the existing `formatMoney` utility and farm currency. This presentation change must not alter backend calculations or financial values.

## Farm-worker form usability

- Money fields are entered in naira (₦), not kobo. The renderer parses the input string into integer kobo before sending the existing backend API payload; no floating-point money arithmetic is used.
- Fields use persistent visible labels instead of relying on placeholders that disappear during typing.
- Batch creation and feed purchases select registered suppliers by name instead of asking operators to type database IDs.
- Tables, table headings, navigation labels, and keyboard shortcut hints use readable text sizes intended for the 1366×768 desktop target.
- The shell name and initials are derived from the saved farm profile.


## Single collapsible navigation

- One sidebar contains the nine existing top-level destinations. Do not render a second activity rail with duplicate destinations.
- The collapse/expand control switches between a labeled sidebar and a compact icon rail. Each icon retains an accessible name and tooltip.
- The compact-mode preference is stored locally in renderer localStorage under `pomas:sidebar-collapsed`; it does not require a backend request or network access.
- The active page and subpage remain encoded in the URL hash when the sidebar is collapsed or expanded.
- Account opens the existing Settings → App, appearance & account section. Settings remains available in the main navigation.
- The sidebar and main-content offset adapt at narrow viewport widths without introducing another route system or farm module.

## Theme selector accessibility

- The theme selector uses its visible “Theme” label as its accessible name through the wrapping label element; do not add a competing `aria-label`.
- Keep the selector keyboard-operable and retain readable label/control contrast in light, dark, and automatic themes.


## First-run farm dashboard

- A genuinely empty farm shows an actionable setup sequence instead of a dashboard full of unexplained zeros: create a house/pen, create a batch, set up and record feed, then enter daily records.
- Each action navigates to an existing page/tab using the local hash router. No route dependency or backend feature is added.
- The empty state is inferred from the absence of houses, batches, calculated bird/financial/egg activity, feed types, and audit events. A zero metric by itself does not trigger it.
- No sample or demo data is inserted. Existing/populated farms retain their normal dashboard.


## Dashboard invalidation after mutations

- Successful write requests from the shared API client emit one `pomas:data-mutated` browser event containing the request path and method. Failed writes and read requests do not emit it.
- The app shell filters those events to farm, batch, feed, health/operations, production, inventory, sales, expense, pricing, and farm-setting endpoints, then refreshes farm dashboard metrics, attention items, audit activity, reports, and the selected batch dashboard.
- Dashboard refreshes must not depend on unrelated collection lengths. The mutation event is the invalidation signal; dashboard/report GET requests do not emit mutation events, preventing refresh loops.
- Vitest covers both successful-write event emission and dashboard/attention reload after a relevant mutation. Runtime validation should include mortality, culling, feed use/purchase, egg collection/sale, health/vaccination, inventory movement, sales, expenses, and relevant settings.


## Actionable dashboard attention items

- Alerts with known routes render as keyboard-accessible buttons. Alerts with missing required identifiers or an unsupported type remain readable but are not clickable.
- Supported destinations: missing daily record → Daily Work / Daily record; high mortality → Flocks / Batch dashboard; vaccination due → Health / Vaccination; production drop → Production / Egg production; batch nearing sale → Production / Weight & growth; low feed → Feed & Stock / Purchases; low/insufficient inventory → Feed & Stock / Other stock; overdue backup → Settings / Backup & restore.
- Alert context is encoded in additional hash-route segments (batch ID, feed type ID, inventory item ID, and/or business date). The route resolver continues to use the first two segments for page and tab selection; the app reads only allow-listed context keys and pre-fills the corresponding form state.
- On direct application startup with a contextual alert hash, a valid batch ID from the hash takes priority over the remembered/default batch while farm data loads, so the selected batch remains aligned with the alert.
- Backend feed-stock alerts expose `feedTypeId`; low and insufficient inventory alerts expose `itemId`. Display names are not used as database identifiers.
- Successful writes emit the shared mutation invalidation event. Relevant mutations refresh farm dashboard metrics, attention alerts, audit activity, reports, and the selected batch dashboard.
- Vitest covers all supported alert mappings, missing/invalid context, encoded context parsing, and a dashboard-to-daily-record navigation with batch/date prefilled. Backend integration tests assert that stock alerts include their navigable IDs.


## Light-theme IDE shell tokens

- The activity bar, sidebar, workspace, cards, hover states, focus ring, tabs, and scrollbar use dedicated semantic IDE tokens.
- Every shell token used by navigation and page tabs has an explicit light-theme value. Keep these values readable against the light surfaces and preserve a visible keyboard focus indicator.
- CSS tests verify token presence and selector usage without depending on formatting or whitespace.


## Getting-started checklist and contextual help

- The dashboard uses a persistent, dismissible getting-started checklist after the farm has moved beyond the completely empty first-run state. Checklist completion is derived from current houses, batches, active feed-type setup, and positive feed stock reported by the backend dashboard; it is not inferred from house creation alone.
- The checklist disappears automatically when every setup task is complete. Dismissal is stored as a renderer preference only and never changes farm records. No demo records are created.
- The application shell shows concise purpose, primary workflow, and a relevant next action for each of the nine top-level pages. Actions use existing local hash routes and remain usable offline.
- Keep help copy short and actionable. Empty states should tell the operator what to do next rather than presenting unexplained blank tables.
- Vitest covers checklist completion/dismissal and page-help route actions. Runtime review should inspect the dashboard with a clean disposable farm, a partially configured farm, and a fully configured farm, plus help on every top-level page.


## Guided product tour

- The persistent page guide includes a Start guided tour action that launches an interactive spotlight walkthrough.
- The walkthrough covers all ten top-level destinations and their operational tabs using the existing hash router.
- Users can navigate backward or forward, skip or close the tour, press Escape to exit, and use the arrow keys.
- The tour works offline and adds no dependency or demo records.


## Pomas brand and theme system

- Pomas is the product brand. Farm names such as “Grantino” are data from the saved farm profile, not fixed product branding.
- Product-level sign-in and navigation branding must say Pomas / poultry farm management; farm-specific names are shown only in active farm context.
- Both light and dark themes use the Pomas teal-and-gold identity. Light mode uses `#F4F7F9` backgrounds, white surfaces, deep teal `#1A4D5C`, and amber/gold accents. Dark mode uses near-black and charcoal surfaces with gold `#F4B41A` highlights.
- Status colours remain semantically distinct: green for healthy, red for critical/error, and amber for warnings. Gold is not used as small text on white.
- Theme preferences are stored under `pomas:theme`; existing `grantino:theme` preferences are read as a migration fallback.
- Keep theme changes CSS-only; do not introduce CDN fonts, external assets, or network dependencies into the offline desktop renderer.
\n\n## Account sign-out and responsive sidebar\n\n- Settings → App, appearance & account includes a visible Sign out action for the current authenticated session.\n- Sign out calls the existing local auth logout endpoint and returns to the sign-in screen; failures stay visible and do not pretend the session ended.\n- The sidebar collapse/expand button remains available at narrow widths. Expanded mode shows labels and farm context; collapsed mode shows the compact icon rail. The preference persists locally and does not change the current route.\n- Verify sign out, failed sign out, and sidebar collapse/expand at desktop and narrow window sizes.\n