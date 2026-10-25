# Content Security Policy: development and packaged renderer

Date: 2026-10-25

## Decision

Keep the packaged renderer CSP strict and separate it from development tooling. The packaged policy permits same-origin bundled scripts/styles and the local Spring Boot API at `http://127.0.0.1:18942` only. The packaged backend ignores the development port environment override so the policy and listener remain aligned.

Electron/Vite development receives a serve-only CSP that permits the local Vite HMR WebSocket on port 5173. The browser-only development workflow uses its own equivalent development policy. Both development policies retain `'unsafe-inline'` in `style-src` because Vite injects development CSS. Neither policy permits inline scripts or `'unsafe-eval'`.

The packaged policy intentionally does not include `'unsafe-inline'`, wildcard ports, Vite origins, or WebSocket sources. The build runs an artifact check against the generated renderer HTML to prevent development-only sources from shipping.

## Verification

- Run `npm run build`; it must fail if the generated renderer CSP differs from the approved production directives.
- Run `npm test -- --run` and `npm run lint`.
- Build and install the Windows package, open DevTools, and inspect Console/Network while loading the dashboard, navigating pages, switching themes, and opening charts. Confirm no CSP blocked-resource errors and confirm API traffic targets `127.0.0.1:18942`.

The installed-app smoke test must be recorded separately from source/build validation; a successful build alone does not prove runtime behavior.
