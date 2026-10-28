# Pomas UI theme decision

**Date:** 2026-10-27  
**Status:** Approved by product owner  
**Supersedes:** Earlier Pomas theme decisions, including the 2026-10-26 theme guidance.

## Decision

Pomas uses deep teal `#1A4D5C` and golden yellow `#F4B41A` as the brand palette. This decision controls all renderer pages and components.

### Light mode

- Use white surfaces. In Dark mode, white page and panel surfaces must become black or near-black.
- Use deep teal for primary actions, headings, and accent text. Any gold brand accent in Dark mode maps back to teal in Light mode.
- Use gold for fills, indicators, and focus accents only. Gold must never be text on white.
- Primary buttons use white text on a teal background.

### Dark mode

- Use black `#000000` and near-black `#0A0A0A` surfaces.
- Use off-white body text and gold accents.
- Replace teal brand accents with golden yellow in Dark mode, including active navigation indicators and primary actions. Do not use teal as a visible brand accent in Dark mode. Keep contrast-safe text roles: gold text on black and black text on gold.
- Primary buttons use black text on a gold background.

### Accessibility and implementation

- Warning, critical, and error colours remain semantically distinct from brand colours.
- All colours are defined through semantic CSS tokens. Screens must not introduce their own hardcoded hex values.
- Auto, Light, and Dark remain supported. Auto follows the system preference.
- Keep normal text contrast at or above 4.5:1 for the checked token pairs. Keep focus indicators clearly visible against adjacent surfaces.
- Update `docs/accessibility.md` and `scripts/verify-a11y.mjs` when tokens or required contrast pairs change.
- Automated token checks do not establish that every rendered text element is accessible. Runtime review is still required at 1366×768 and 1100×650 in Auto, Light, and Dark.

## Pre-launch review requirements

Review all nine canonical pages in both light and dark themes, including first-run and populated data states. Verify primary actions, page/tab labels, table semantics, empty states, focus order, and contained horizontal scrolling. Report separately what was tested in the running app and what was only inspected in source.
