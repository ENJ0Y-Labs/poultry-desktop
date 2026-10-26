# Decision: Teal and gold theme system

Date: 2026-10-26
Related issue: #93

## Decision
Use deep teal (`#1A4D5C`) as the primary action and brand color, and golden yellow (`#F4B41A`) as the accent color across light and dark appearance modes.

## Application
- Dark mode uses dark teal-tinted surfaces, light primary text, readable muted text, and gold accents.
- Light mode uses light surfaces and dark text. Teal is used for readable accent text; gold is reserved for fills, indicators, and accents where contrast is sufficient.
- Primary buttons use deep teal with white text in both themes.
- Hover, selected, and keyboard-focus states remain visible and theme-aware.
- Existing Auto/Light/Dark preference and persistence behavior is retained.
- Severity colors remain semantically distinct from brand colors.

## Validation
Run `npm test`, `npm run build`, and `npm run lint`. The build includes the automated accessibility contrast check. Manually inspect Dashboard and at least one data-entry screen in Light and Dark modes at 1366×768 and 1100×700. Do not describe manual inspection as complete unless performed.
