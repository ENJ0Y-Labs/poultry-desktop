# Pomas UI/UX Audit and Improvement Report

**Audit date:** 12 November 2026  
**Environment:** isolated temporary project copy and synthetic SQLite database  
**Viewport coverage:** 1366×768 and 1100×650; collapsed navigation and batch creation wizard inspected.

## Safety and method

The application renderer was launched from a disposable copy at `%LOCALAPPDATA%/Temp/pomas-ui-audit-17fdb44a6d0249acb253984aa034738f`, with the backend on `127.0.0.1:18943`, Vite on `127.0.0.1:5174`, and database at that copy's `data/pomas-audit.db`. Before launch the audit copy had no database; the isolated backend log and health check confirmed the temporary DB path and schema 33. The original `data/poultry.db` SHA-256 was `345511EA8097B5585EC0310D57BCFBAF9C01A0719FD3A67AF89EDE7A7DAB9221` before and after the audit. All created account, farm, houses, and batches were synthetic.

The bundled Electron executable was unavailable in the installed dependency tree (`node_modules/electron/dist/electron.exe` was absent/empty), so the Electron shell could not be launched. As the safe alternative, the project's documented browser-only Vite workflow rendered the actual Pomas React application against the isolated Spring Boot API and database in headless Chrome. No Electron-specific window behavior is claimed as inspected.

## Coverage

Captured the owner and farm setup flows, then visited 29 route/tab destinations: Dashboard; Batch dashboard, Batches, Houses; Daily record, Mortality, Culling, Water; Feed, Purchases, Other stock, Suppliers; Health records, Drugs, Vaccination; Egg production, Weight & growth; Sales, Customers, Expenses; Farm report, Batch report, Compare batches; Import / Export; Farm profile, Farm defaults, App/appearance/account, Backup/restore, Pricing. Full-page captures are included for the long Dashboard, Houses, Daily record, Feed, Health, Eggs, Farm report, Sales, and Farm profile pages. The batch-registration wizard and both sidebar states were also captured. The empty-state pages are preserved as seen; the batches and houses lists contain two synthetic records.

Screenshots: [before screenshots](before/), [before route captures](before/routes/), [before interactions](before/interactions/), [route evidence JSON](before/routes/route-audit.json), [after full-page and scrolled route captures](after/routes/), [after wizard and responsive interactions](after/interactions/).

## Findings

| Priority | Page | Evidence | Observed issue and usability impact | Recommendation |
|---|---|---|---|---|
| **High** | Batches & bird costs | [Before, 1366×768](before/routes/flocks-flocks.png), [Before, 1100×650](before/interactions/batches-1100x650.png), [After, 1366×768](after/routes/flocks-flocks-top-1366x768.png), [After full page](after/routes/flocks-flocks-full-1366.png) | Batch codes and the “View” and “Mark sold” action labels broke across lines in the table at the standard viewport. At 1100px the page also gains horizontal scrolling and leaves the wide table difficult to scan. Narrow identifiers slow row comparison and make actions harder to recognize. | Keep first-column identifiers and action controls together; contain unavoidable table width in a keyboard-scrollable table region. **Implemented:** data cells use normal word wrapping, first-column values and table actions remain on one line. |
| **Medium** | Dashboard | [Before, 1366×768](before/routes/dashboard-overview.png), [Before, 1100×650](before/interactions/dashboard-1100x650.png), [After, 1366×768](after/routes/dashboard-overview-top-1366x768.png), [After full page](after/routes/dashboard-overview-full-1366.png) | The reporting controls form a tall right-hand stack while the “Farm dashboard” heading sat at the bottom of the same header card, producing a large asymmetric blank field and weak alignment. | Align the heading and reporting controls around their shared vertical center. **Implemented.** |
| **Medium** | Register batch wizard | [Before step 1](before/interactions/register-batch-form.png), [After step 1](after/interactions/register-batch-step1.png), [After step 2](after/interactions/register-batch-step2.png) | The five-step progress labels were clipped to fragments in a 620px-wide dialog because the labels competed for a narrow left column. The user's position and upcoming steps were hard to read. | Use a compact numbered progress row with the active step title visible and retain each step name for assistive technology. **Implemented.** |
| **Low** | Farm dashboard / reports | [Dashboard full page](before/routes/dashboard-overview-full.png), [Farm report](before/routes/reports-farm-report.png) | Date range controls stack as a tall column on the right, consuming more vertical space than the adjacent heading and pushing report content down. This is especially noticeable at the 1100×650 laptop size. | Use a compact horizontal preset/date control row where space permits and retain a clear stacked layout only at narrower widths. |
| **Low** | Daily record | [Daily record](before/routes/daily-work-daily-record.png) | The page guide tells the operator to select a batch and farm date, but no batch/date selector is visible in the captured daily record workspace. Context is not obvious from this screen, which can make it unclear which flock is receiving a record. | Make the active batch and business date visible next to the daily record actions, or make the existing global context control explicit. Verify interaction behavior before changing data flow. |

### Confirmed behavior and limits

- The collapsed sidebar retains the Flocks route and presents consistent icon targets. The top-left control has the accessible name and tooltip “Collapse sidebar”; it changes to the expand control after collapse. See [collapsed sidebar](before/interactions/sidebar-collapsed.png).
- The batch wizard is centered and fits the 1366×768 viewport. Its progress labels need a responsive treatment (finding above). See [wizard capture](before/interactions/register-batch-form.png).
- All 29 destinations rendered. One browser console resource error was recorded as a 404; the route sweep did not identify which asset caused it, so it is not treated as a confirmed user-facing defect.
- Transaction-ledger pages are captured in their authentic empty state because only synthetic farm/house/batch records were created. The audit did not seed representative drug, feed, sales, expense, egg, or inventory transactions; those tables and their populated states remain to be reviewed in a follow-up pass.
- The 1100px desktop minimum is the project's documented supported width. Tablet/mobile layouts and Electron-native behavior were not validated.

## Prioritized implementation plan

1. **High:** keep data-table identifiers and actions readable; retain intentional horizontal scrolling for genuinely wide datasets.
2. **Medium:** align dashboard heading and reporting controls; redesign the batch wizard step indicator to avoid clipped labels.
3. **Medium:** expose the active batch and farm date clearly on the daily-record surface after verifying the existing context interaction.
4. **Low:** compact date range controls on Dashboard and Reports at laptop widths.
5. **Verification:** repeat route screenshots at both supported widths, check sidebar navigation and wizard validation, run renderer tests/lint/build, and compare screenshots. Ledger-populated states should be added using synthetic API records only.

## Changes made after capturing the before screenshots

The first three findings were addressed in the source. Data-table identifiers and actions stay on one line, the dashboard header aligns its heading and date controls vertically, and the wizard uses compact progress markers with accessible names. The remaining findings are documented recommendations and were not changed in this pass.

## Verification

- Full frontend test suite: **44 files, 321 tests passed**.
- `npm run lint`: passed.
- `npm run build`: passed, including CSP, production renderer, contrast/minimum-size accessibility, and font asset checks.
- The actual isolated browser renderer was revisited after the changes. All 29 routes have 1366×768 top screenshots and full-height captures; pages longer than one viewport also have middle/bottom scrolled captures (34 extra views). Dashboard and batch list were also recaptured at 1100×650 after the changes, and both sidebar states were recaptured. Long content and the batch table were visually checked after capture.
- The batch wizard was opened, advanced to House selection, returned to step 1, and closed without submitting or changing farm records. After screenshots are in `after/interactions/`.
- A single browser resource 404 was observed during the route sweep and could not be attributed to a visible page asset. No claim is made that this was resolved.
- Production database SHA-256 remained unchanged at `345511EA8097B5585EC0310D57BCFBAF9C01A0719FD3A67AF89EDE7A7DAB9221`.

## Dashboard and Quick actions follow-up

After the audit, a follow-up reproduced the scattered Dashboard KPI content and the Quick actions popover behavior in the isolated renderer. Dashboard metrics now render as three stable grids: three headline cards, five operational cards, and ten flock/facility cards. KPI buttons use content-surface styling instead of inheriting the global primary-action button color. Quick actions now uses the shared centered modal with a backdrop, bounded scrolling, focus handling, and Escape/backdrop close behavior.

The updated states were captured from the running isolated renderer: [Dashboard KPI cards](after/dashboard-fix/dashboard-kpi-cards-visible.png), [Quick actions modal](after/dashboard-fix/quick-actions-modal.png). Targeted renderer tests passed (**46 tests across four files**) and `npm run lint` passed.
