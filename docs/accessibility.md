# UI accessibility review

## Scope and approach

Pomas supports dark and light themes. The accessibility overrides are loaded after the main stylesheet and use dedicated semantic color tokens for important text, rather than relying on legacy palette aliases that also represent backgrounds.

The repeatable build check in `scripts/verify-a11y.mjs` computes WCAG relative-luminance contrast ratios from the actual literal foreground/background tokens used by the accessibility stylesheet. It checks primary, secondary, tertiary, shortcut, and accent-navigation text in both themes. It also checks minimum font sizes for primary navigation, form labels, table headers, metric labels, keyboard shortcuts, and the status bar.

## Text-size policy

- Primary navigation and persistent form labels: at least 12 px, with navigation raised to 13 px.
- Table headers and metric labels: at least 12 px.
- Shortcut hints: 12 px and secondary-text contrast; visually subordinate through muted color and compact treatment, not illegibility.
- Status bar and connection metadata: 11 px because they are secondary metadata, not primary workflow instructions.
- Decorative uppercase section kickers may remain smaller than body text only where they are not the sole carrier of meaning. They use the stronger secondary-text token.
- Form helper text remains 11 px as supplementary guidance. Important errors are 13 px and use the existing error color tokens.

WCAG contrast thresholds used by the checker are 4.5:1 for normal text. The current checked text pairs are not large-text exceptions. The 3:1 large-text allowance is therefore not used to excuse small labels.

## Skip navigation

The application shell places a `Skip to main content` link before the primary work-area and sidebar navigation in DOM order. It stays outside the normal visual layout until focused, then appears at the top of the window with a high-contrast focus outline. Activating it focuses the main landmark without changing the hash route, so the existing hash-based navigation does not interpret the skip action as a page change.

Keyboard-only smoke test:

1. Launch the app and sign in.
2. Press `Tab` once from the page's initial focus state. The first focusable control should be `Skip to main content`, visibly displayed.
3. Press `Enter`. Focus should move to the main content landmark; the URL hash and active page should remain unchanged.
4. Press `Tab` again and confirm focus continues from the main content rather than restarting at the primary navigation.
5. Repeat on Dashboard and Flocks (including a nested tab if useful). Confirm the link remains visually hidden when it is not focused and does not occupy layout space.

## Required manual rendered review

The token check does not measure every live text node. To re-measure actual rendered text counts and computed styles, open DevTools in the running desktop app and paste the contents of `scripts/audit-rendered-ui.js` into the Console. Run it once with Light selected and again with Dark selected. Repeat at 1366×768 and 1100×700. Save the returned summary/counts with the release checklist. Do not reuse counts from a previous build.

The browser-console audit reports visible text nodes measured, contrast failures, essential-text contrast failures, text below 12 px, and essential text below 12 px. Review each example rather than blindly enlarging decorative labels.

Automated token checks are a guardrail, not a substitute for checking the actual rendered app. Before release, run `npm run dev` and review both Light and Dark themes at 1366×768 and 1100×700. Check Dashboard, Flocks, Daily Work, Feed & Stock, Health, Production, Sales & Money, Reports, and Settings.

Verify:
- Main navigation labels are readable and keyboard shortcuts are visible without competing with the labels.
- Form labels remain visible when fields contain values; helper text and validation errors are legible.
- Status, warning, critical, and error messages remain distinguishable in both themes.
- Tables, metric cards, tabs, and focus indicators remain legible without clipping or horizontal layout damage.
- Keyboard-only navigation has a visible focus indicator and logical Tab order.

## Exceptions and limitations

The small status bar and helper text remain 11 px because they are secondary/supporting content. They are not exempt from contrast checks: the semantic secondary text token is checked against both dark and light surfaces. Decorative icons and non-text separators are not text-contrast pairs; their non-text contrast and meaning should be checked during manual review.

The script measures the semantic color tokens and verifies specified CSS rules. It does not launch Electron, inspect computed browser styles, or count every text node at runtime. Do not report the whole UI as WCAG-conformant solely because this check passes. Record the actual manual results before release.
