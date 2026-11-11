# Responsive layout audit

## Decision

Pomas is a **desktop-only Windows application**. The supported minimum window size is **1100×650 CSS pixels**. The normal initial window is **1366×768**. The Electron shell already enforces `minWidth: 1100` and `minHeight: 650`; the renderer previously contradicted that contract by switching to a phone-like navigation layout below 700px while also declaring a 1100px minimum body width.

The narrow-screen CSS did not prove phone support. It produced two competing layout models, depending on which media rule won. The fix removes the mobile-shell transformations and keeps the desktop canvas consistent with the Electron minimum. No hamburger menu was added because no actual navigation failure at a supported width has been reproduced.

## Supported dimensions

| Viewport/window | Status | Expected behavior |
| --- | --- | --- |
| 360×800 | Unsupported diagnostic viewport | Desktop canvas remains at least 1100px wide; horizontal viewport scrolling is expected. Do not claim mobile usability. |
| 390×844 | Unsupported diagnostic viewport | Same as 360px; use only to inspect clipping/regressions in the visible portion. |
| 768×1024 (tablet portrait width) | Unsupported diagnostic viewport | Desktop canvas remains at least 1100px wide; horizontal viewport scrolling is expected. |
| 1024×768 | Unsupported | Electron cannot be resized this narrow. Browser rendering is diagnostic only. |
| 1100×650 | **Supported minimum** | All primary areas remain reachable; dashboard and forms fit the available content area; wide tables scroll inside their table container rather than expanding the page. |
| 1366×768 | **Default target** | Full desktop layout with the collapsible sidebar, dashboard, forms, and tables. |
| 1920×1080 | **Supported desktop** | Content remains aligned and readable without stretching tables or cards beyond their useful width. |

A 360px browser viewport is not a supported layout width. The supported-width acceptance criterion is no unintended page-level overflow at 1100×650 and larger. At unsupported narrow widths, horizontal scrolling is intentional and is not a mobile-support claim.

## Source findings and changes

- Electron's `BrowserWindow` opens at 1366×768 and already sets `minWidth: 1100` and `minHeight: 650`.
- The stylesheet's base `body` rule sets `min-width: 1100px`, but an older `max-width: 700px` rule overrode it to `0` and changed the shell/navigation to a mobile-like layout.
- A second `max-width: 700px` rule also moved navigation into a stacked mobile layout.
- Removed the narrow-screen shell transformations. Kept dashboard and content-grid breakpoints that improve density inside the desktop canvas.
- A single collapsible sidebar is the primary navigation. The sidebar begins at the left edge, and the main content offset matches the expanded or collapsed width. No new dependency or hamburger menu was introduced.
- The expanded brand uses the Pomas logo and wordmark with a separate collapse button. In collapsed mode, the same 26px logo is centered in a 40px expand target. Primary navigation glyphs are 20px in 42px targets.
- Page templates share consistent card, heading, table, tab, and form treatments while retaining their domain-specific layout and existing data/actions.
- The existing `.table` container uses `overflow: auto`, so wide row content should scroll within the table instead of forcing the entire page wider. This behavior still requires runtime confirmation.

## Runtime validation checklist

The source change has been made, but **browser emulation and packaged Electron resizing have not been executed in this environment**. Do not mark the following checks passed until they are performed on the branch.

### Browser renderer

1. Run `npm run dev:browser:all`, open the renderer in Chrome, and open DevTools.
2. Inspect 360×800, 390×844, 768×1024, 1024×768, 1100×650, 1366×768, and 1920×1080 with device emulation or responsive mode.
3. At 360px, 390px, and tablet widths, confirm the app remains explicitly a desktop canvas and does not switch to the removed phone-navigation layout. Horizontal scrolling is expected because these widths are unsupported.
4. At 1100×650, 1366×768, and 1920×1080, inspect Dashboard, Flocks, Daily Work, Feed & Stock, Health, Production, Sales & Money, Reports, Import / Export, and Settings with both expanded and collapsed sidebar states. Inspect every available tab, key forms, tables, dialogs, and chart state without writing test data to a live farm.
5. Inspect dashboard metric cards, attention/feed panels, page tabs, status indicators, every visible form, and each table. Confirm buttons/inputs are not clipped and table overflow stays inside its table container.
6. Record any horizontal overflow with the element responsible, viewport size, page/section, and a screenshot. Treat only unintended overflow at supported widths as a defect.

### Packaged Electron window

1. Run `npm run package`, install the generated `release/PomasSetup.exe` in a test location, and launch it.
2. Resize the window to the enforced minimum of 1100×650, then restore it to 1366×768.
3. Repeat the primary-navigation, dashboard, forms, tables, and status-indicator checks above.
4. Confirm Electron will not resize below 1100×650 and that the renderer remains usable at the minimum.
5. Save screenshots and record the commit SHA and pass/fail result for each supported size.

### Results ledger

| Check | Current status |
| --- | --- |
| Source inspection of Electron min/max layout contract | Complete: 1100×650 minimum, 1366×768 default |
| Remove contradictory narrow-shell CSS | Implemented |
| 360px / 390px / tablet browser emulation | Not run |
| All 28 page/tab routes at 1366×768 | All 28 routes were navigated and full-page plus top/middle/bottom captures were generated with the expanded sidebar. The screenshot surface limitation below means the captures do not conclusively prove a 1366px-wide visual review. No vertical nested scrolling was found in the route DOM checks. |
| 1100×650 representative routes | Dashboard, Flocks, Daily Work, Feed & Stock, Health, Production, Reports, Import / Export, and Settings were captured at top/middle/bottom with the expanded sidebar. Dashboard, Feed & Stock, and Settings were also captured collapsed. |
| Nested scrolling at 1100×650 | Sidebar navigation scrolls vertically (495px content in a 427px viewport, 68px maximum scroll); top/middle/bottom states were captured and reviewed. No content was covered by the fixed account footer. |
| 1920×1080 | Playwright DOM layout was measured at the requested virtual viewport. The browser screenshot surface remained at its fixed ~1100px size, so visual verification at 1920×1080 is incomplete; the generated 1920 captures are not reliable proof of that viewport. |
| Document-level horizontal overflow | No overflow observed in the reviewed 1366×768 routes or the captured 1100×650 representative routes. |
| Before/after page screenshots | After screenshots are organized by route in the session UI audit capture folder. A pre-change dashboard reference was captured at 1366×768; pre-change screenshots for other pages were not retained. |
| Read-only form interaction | Health-record dialog opened and closed without submitting; no farm data was written. Theme switch keyboard/persistence and backup switch draft/revert behavior were also checked without saving farm data. |
| Packaged Electron resize to 1100×650 | Not run |
| Packaged Electron checks at 1366×768 | Not run |

The browser route/overflow check covers the local Vite renderer, not packaged Electron. No unsupported-width or packaged-window result is implied. A CSS source review is not a substitute for running the renderer.

The browser screenshot surface is fixed at approximately 1100×650 in this environment. Playwright can report and lay out a larger virtual viewport, but screenshots from that page still show the fixed-size surface; do not treat those images as visual verification at the requested larger size. The full-page screenshot helper also reset its capture width to approximately 1089px. Larger-size visual claims at 1366×768 and 1920×1080 therefore require a browser or Electron window that can actually render and capture those dimensions.
