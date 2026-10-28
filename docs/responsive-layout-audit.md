# Responsive layout audit

## Decision

Pomas is a **desktop-only Windows application**. The supported minimum window size is **680×600 CSS pixels** so Windows can snap Pomas into a half-screen layout on common laptop displays. The normal initial window is **1366×768**. Electron enforces `minWidth: 680` and `minHeight: 600`. At narrow desktop widths the renderer keeps desktop navigation, hides sidebar labels, and stacks dense grids.

The narrow-screen CSS did not prove phone support. It produced two competing layout models, depending on which media rule won. The fix removes the mobile-shell transformations and keeps the desktop canvas consistent with the Electron minimum. No hamburger menu was added because no actual navigation failure at a supported width has been reproduced.

## Supported dimensions

| Viewport/window | Status | Expected behavior |
| --- | --- | --- |
| 360×800 | Unsupported diagnostic viewport | Desktop canvas remains at least 680px wide; horizontal viewport scrolling is expected. Do not claim mobile usability. |
| 390×844 | Unsupported diagnostic viewport | Same as 360px; use only to inspect clipping/regressions in the visible portion. |
| 768×1024 (tablet portrait width) | Unsupported diagnostic viewport | Desktop canvas remains desktop-oriented; horizontal viewport scrolling is expected. |
| 1024×768 | Supported | Desktop layout; sidebar labels may remain visible depending on available width. |
| 680×600 | **Supported minimum** | All primary areas remain reachable; dashboard and forms fit the available content area; wide tables scroll inside their table container rather than expanding the page. |
| 1366×768 | **Default target** | Full desktop layout with activity bar, section navigation, dashboard, forms, and tables. |

A 360px browser viewport is not a supported layout width. The supported-width acceptance criterion is no unintended page-level overflow at 680×600 and larger. At unsupported narrow widths, horizontal scrolling is intentional and is not a mobile-support claim.

## Source findings and changes

- Electron's `BrowserWindow` opens at 1366×768 and already sets `minWidth: 680` and `minHeight: 600`.
- The stylesheet previously set a 1100px body minimum and had competing narrow-screen rules. The renderer now permits the Electron minimum and applies one final narrow-desktop layout override.
- A second `max-width: 700px` rule also moved the activity bar and sidebar into a stacked mobile layout.
- Removed both narrow-screen shell transformations. Kept dashboard and content-grid breakpoints that improve density inside the desktop canvas.
- The existing activity bar and section navigation remain the primary navigation. No new dependency or hamburger menu was introduced.
- The existing `.table` container uses `overflow: auto`, so wide row content should scroll within the table instead of forcing the entire page wider. This behavior still requires runtime confirmation.

## Runtime validation checklist

The source change has been made, but **browser emulation and packaged Electron resizing have not been executed in this environment**. Do not mark the following checks passed until they are performed on the branch.

### Browser renderer

1. Run `npm run dev:browser:all`, open the renderer in Chrome, and open DevTools.
2. Inspect 360×800, 390×844, 768×1024, 1024×768, 680×600, and 1366×768 with device emulation or responsive mode.
3. At 360px, 390px, and tablet widths, confirm the app remains explicitly a desktop canvas and does not switch to the removed phone-navigation layout. Horizontal scrolling is expected because these widths are unsupported.
4. At 680×600 and 1366×768, inspect Dashboard, Flocks, Daily Work, Feed & Stock, Health, Production, Sales & Money, Reports, and Settings. Confirm each primary area can be reached through the activity bar and sidebar.
5. Inspect dashboard metric cards, attention/feed panels, page tabs, status indicators, every visible form, and each table. Confirm buttons/inputs are not clipped and table overflow stays inside its table container.
6. Record any horizontal overflow with the element responsible, viewport size, page/section, and a screenshot. Treat only unintended overflow at supported widths as a defect.

### Packaged Electron window

1. Run `npm run package`, install the generated `release/PomasSetup.exe` in a test location, and launch it.
2. Resize the window to the enforced minimum of 680×600, then restore it to 1366×768.
3. Repeat the primary-navigation, dashboard, forms, tables, and status-indicator checks above.
4. Confirm Electron will not resize below 680×600 and that the renderer remains usable at the minimum.
5. Save screenshots and record the commit SHA and pass/fail result for each supported size.

### Results ledger

| Check | Current status |
| --- | --- |
| Source inspection of Electron min/max layout contract | Complete: 680×600 minimum, 1366×768 default |
| Remove contradictory narrow-shell CSS | Implemented |
| 360px / 390px / tablet browser emulation | Not run |
| Dashboard/forms/tables/navigation at 680×600 in browser | Not run |
| Dashboard/forms/tables/navigation at 1366×768 in browser | Not run |
| Packaged Electron resize to 680×600 | Not run |
| Packaged Electron checks at 1366×768 | Not run |

Do not report the runtime acceptance criteria as passed until the unchecked tests are completed. A CSS source review is not a substitute for running the renderer.
