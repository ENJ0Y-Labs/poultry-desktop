# Pomas UI/UX audit — 2026-11-12

## Scope and method

This is a partial source-and-browser audit of the current renderer. It reviewed repository instructions and UI guidance, the app shell and navigation, shared table/filter, modal, drawer, quick-action and chart components, renderer styles, and existing responsive/accessibility/form/navigation documentation. The live browser at `127.0.0.1:5174` opened to the Pomas sign-in screen; no authenticated session or disposable farm account was available. The ten destination pages, their forms/tables/charts, and authenticated dialogs therefore could not be visually or interactively inspected in this pass.

No real farm was accessed or changed. The browser visit does not establish a visual pass at a supported window size. The existing responsive audit documents the 1100×650 minimum and limitations of the available screenshot surface; packaged-window checks remain unrun. Screen captures from an unsupported-width or fixed-size browser surface are not evidence for supported-window behavior.

## Existing strengths

- One hash-based navigation shell, collapsible sidebar, global quick actions, keyboard shortcuts, skip link, and page help.
- Semantic Pomas light/dark theme tokens and automated accessibility/token checks.
- Shared `DataTable`, filter controls, pagination, empty/error states, page sections, validated forms, progressive disclosure, and short/complex form patterns.
- Central toast provider, modal system, and contextual drawer with renderer coverage.
- Dashboard metrics use backend data; trend charts preserve missing-day gaps; monetary display and date handling have dedicated utilities.
- The project already documents important limits: client-side filters/pagination operate on loaded rows, and full visual checks require a real supported browser or Electron window.
- A consistent set of inline SVG navigation glyphs is already present; no icon package is installed, so this pass did not add a dependency.

## Prioritized findings

| Priority | Finding | User impact | Recommended work |
| --- | --- | --- | --- |
| P1 | Renderer minimum-width contract was contradicted by a trailing `body { min-width: 0 }` and shell-specific narrow-screen overrides. This disagreed with the 1100px Electron minimum and the existing responsive audit. | Unsupported narrow windows could show a different, cramped shell and invalidate the documented desktop-only behavior. | Restored the 1100px canvas contract and removed the late shell transformations. Added a CSS regression assertion. |
| P1 | Shared row-action UI exists, but module pages have not integrated it; the existing row-action doc explicitly records this as incomplete. | Record-specific actions are less discoverable and inconsistent; users may need to navigate away or rely on separate controls. | Integrate page by page using existing workflows and backend endpoints; do not add unrestricted edits to posted transactions. |
| P1 | Tables paginate and filter only the rows already loaded; they are not server-paginated. | Large histories can still require large API payloads, while local search cannot find unloaded records. | Keep the “loaded records” scope explicit. Bound high-volume endpoints before claiming complete search or scalability. |
| P2 | The app has reusable visual primitives, but shared styling is spread across a long stylesheet and page-specific exceptions. | Later changes can diverge or accidentally override shared behavior. | As screen work proceeds, consolidate only repeated patterns into shared primitives/tokens; avoid a risky wholesale CSS rewrite. |
| P2 | In collapsed mode, the logo doubled as the expand button and the panel icon appeared only on hover/focus. | The action was not continuously discoverable and made the brand mark behave like a control. | Keep the logo decorative and show a dedicated, labeled panel toggle in both states. |
| P2 | Existing docs list the key manual review matrix, but packaged Electron checks and full visual inspection are still unverified. | Source/tests cannot reveal all clipping, hierarchy, or keyboard flow issues in the actual window. | Run a disposable-data review at 1100×650 and 1366×768, both sidebar states and themes, before claiming visual completion. |

## Implementation in this audit pass

- Reconciled the renderer canvas with the documented 1100px desktop minimum. Removed only the trailing narrow-shell overrides; existing dashboard/grid density breakpoints remain.
- Added a stylesheet regression check for the minimum canvas and for absence of the contradictory narrow sidebar transformation.
- Replaced the hover-only logo-as-expand affordance with a persistent panel toggle that changes direction, accessible name, and tooltip while preserving the selected route and persisted collapsed preference.
- Corrected stale navigation documentation that said there were nine destinations and implied narrow-width shell adaptation.

## Phased plan

1. **Audit and contract repair — this pass:** capture source findings and repair the concrete responsive-contract mismatch.
2. **Shared interaction foundation:** migrate record modules to contextual row actions where supported; review remaining table, form, loading, and error-state inconsistencies using shared components.
3. **Core workflows:** inspect Dashboard, Flocks, Daily Work, Feed & Stock, and Health in supported windows and improve only demonstrated friction points.
4. **Remaining workflows:** inspect Production, Sales & Money, Reports, Import / Export, and Settings with the same criteria.
5. **Quality review:** run automated checks, keyboard/accessibility review, and disposable-database manual checks. Record unrun packaged checks honestly.

Each phase should stay within renderer presentation and existing REST workflows unless a real requirement demonstrates a missing API contract. Preserve farm calculations, audit behavior, money formatting, missing-data semantics, and offline operation.

## Manual verification still required

- Provide a shared authenticated test session or authorize a disposable local database/account before reviewing authenticated workflows. Do not enter credentials for or mutate an existing farm.
- Launch the renderer or packaged Electron app at 1100×650 and 1366×768, with expanded and collapsed sidebar.
- Inspect all ten destinations and their tabs, tables, forms, dialogs, drawers, dropdowns, charts, loading/error/empty states, both themes, keyboard order, focus visibility, and internal table scrolling.
- Use a disposable database for any write workflow. Do not use real farm data.
- Capture screenshots from the actual target surface; do not infer visual pass from CSS source or an undersized screenshot service.

