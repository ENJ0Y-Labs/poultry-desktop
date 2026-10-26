# Renderer font audit

## Finding

The repository does not support the claim that the renderer loads ten web-font families. Inspection of this branch found:

- Renderer font files (`.woff`, `.woff2`, `.ttf`, `.otf`, `.eot`): **0 files / 0 bytes**.
- CSS `@font-face` declarations: **0**.
- CSS `@import` declarations: **0**.
- CSS font-file `url()` references: **0**.
- Remote Google Fonts or Typekit references in the renderer entry files and stylesheet: **0**.
- KaTeX dependency in `package.json` / `package-lock.json`: **not present**.

The renderer imports its local `styles.css` and `accessibility.css`. Its CSP permits same-origin scripts/styles and the local API, but does not allow a remote font host. The audit therefore does **not** remove KaTeX or font files: no such dependency or asset was found to remove.

## Retained font and UI use

| Font declaration | UI use | Source |
| --- | --- | --- |
| `"Segoe UI", system-ui, -apple-system, BlinkMacSystemFont, sans-serif` | The single global UI font stack inherited by navigation, forms, tables, dashboard metrics, headings, and supporting text | `app/renderer/src/styles.css` `:root` |

This is a system-font stack, not a downloaded web font. Windows prefers Segoe UI; other environments can use their native system UI font. Inter was removed from the stack because the repository does not ship it, so its availability depended on the machine. No `font-display: swap` rule is needed because there is no web-loaded font.

## Before/after repository asset measurements

These are source/build-artifact inventory measurements, not claims about an observed browser session.

| Measurement | Before change (source inventory) | After change (source inventory) |
| --- | ---: | ---: |
| Renderer font files | 0 | 0 |
| Renderer font asset bytes | 0 B | 0 B |
| `@font-face` declarations | 0 | 0 |
| CSS imports | 0 | 0 |
| CSS font-file URLs | 0 | 0 |
| KaTeX package dependency | absent | absent |
| Font-stack change | Inter plus fallbacks | System UI stack only |

The CSS declaration change adds no network request and no font asset. The production renderer's exact output inventory is measured by `npm run build`, which runs `scripts/audit-font-assets.mjs` after bundling. This repository inspection did not execute a local build, so a post-build byte count and actual browser transfer total have not been independently collected here.

## Runtime verification

Run the following on the same commit in development and in the packaged installer. Do not infer runtime requests from the source audit alone.

1. Run `npm run dev` and open DevTools for the renderer. In **Network**, enable **Disable cache**, clear the log, reload, and filter by `Font`. Also inspect **All** requests for `woff`, `ttf`, `fonts.googleapis.com`, `fonts.gstatic.com`, or `katex`.
2. Confirm no font-file requests occur, and no failed/missing-font messages appear in the Console. Save the Network HAR or screenshots with the commit SHA.
3. Run `npm run package`, install the generated `release/PomasSetup.exe` into a test location, launch it, and repeat the same Network/Console inspection with Electron DevTools. Confirm the renderer opens and the core screens render.
4. Compare the font-filtered transferred bytes and request count before and after the change. If the supplied browser report can be reproduced, preserve its HAR and identify the exact request initiator before removing any package or asset.

## Automated guard

`npm run build` invokes `scripts/audit-font-assets.mjs` after the production renderer is built. It inventories source and bundled font-file counts/bytes and fails if unreviewed font files, `@font-face`, stylesheet imports, remote font-provider references, or CSS font-file URLs are introduced. Review and document any intentional future font before updating this guard.

## Validation status

- [x] Source CSS, renderer HTML/entry imports, dependency manifest/lockfile, and tracked font extensions inspected.
- [x] Unbundled Inter reference removed in favor of a system UI stack.
- [x] Build-time source and packaged-renderer font audit added.
- [ ] Local `npm run build` and automated tests executed.
- [ ] Development Network/Console inspection executed.
- [ ] Installed packaged-renderer Network/Console inspection executed.
- [ ] Runtime before/after transfer totals captured.

The unchecked items require a real local runtime. A GitHub source review cannot truthfully certify that the packaged app starts or that a browser's reported requests occurred.
