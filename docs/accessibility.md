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

## Required manual rendered review

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
