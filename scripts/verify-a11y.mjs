import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const cssPath = resolve("app/renderer/src/accessibility.css");
const css = readFileSync(cssPath, "utf8");

function blockFor(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const matches = [...css.matchAll(new RegExp(`(?:^|\\n)\\s*${escaped}\\s*\\{([^}]*)\\}`, "g"))];
  assert.ok(matches.length, `Missing theme block: ${selector}`);
  return matches.at(-1)[1];
}

function readToken(block, name) {
  const match = block.match(new RegExp(`(?:^|;)\\s*${name}\\s*:\\s*(#[0-9a-f]{3,8})\\s*;?`, "i"));
  assert.ok(match, `Missing literal color token ${name}`);
  return match[1];
}

function rgb(hex) {
  const value = hex.slice(1);
  const expanded = value.length === 3 ? [...value].map((c) => c + c).join("") : value.slice(0, 6);
  return [0, 2, 4].map((offset) => parseInt(expanded.slice(offset, offset + 2), 16) / 255)
    .map((channel) => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
}

function luminance(hex) {
  const [r, g, b] = rgb(hex);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(foreground, background) {
  const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return (values[0] + 0.05) / (values[1] + 0.05);
}

const themes = [
  { name: "dark", selector: ':root:not([data-theme="light"])' },
  { name: "light", selector: ':root[data-theme="light"]' },
];
const tokenNames = {
  surface: "--a11y-surface",
  raised: "--a11y-surface-raised",
  text: "--a11y-text",
  secondary: "--a11y-secondary",
  tertiary: "--a11y-tertiary",
  accent: "--a11y-accent-text",
  activeNav: "--a11y-nav-active-bg",
  shortcut: "--a11y-shortcut-bg",
  warningBg: "--a11y-warning-bg",
  warningText: "--a11y-warning-text",
  criticalBg: "--a11y-critical-bg",
  criticalText: "--a11y-critical-text",
  errorBg: "--a11y-error-bg",
  errorText: "--a11y-error-text",
  actionBg: "--pomas-primary-action-bg",
  actionText: "--pomas-primary-action-text",
  focus: "--pomas-focus-ring",
};

for (const theme of themes) {
  const block = blockFor(theme.selector);
  const tokens = Object.fromEntries(Object.entries(tokenNames).map(([key, token]) => [key, readToken(block, token)]));
  const checks = [
    ["primary text on surface", tokens.text, tokens.surface, 4.5],
    ["primary text on raised surface", tokens.text, tokens.raised, 4.5],
    ["secondary text on surface", tokens.secondary, tokens.surface, 4.5],
    ["secondary text on raised surface", tokens.secondary, tokens.raised, 4.5],
    ["tertiary text on surface", tokens.tertiary, tokens.surface, 4.5],
    ["shortcut hint on raised surface", tokens.secondary, tokens.shortcut, 4.5],
    ["active navigation text on active navigation background", tokens.accent, tokens.activeNav, 4.5],
    ["warning status text", tokens.warningText, tokens.warningBg, 4.5],
    ["critical status text", tokens.criticalText, tokens.criticalBg, 4.5],
    ["error message text", tokens.errorText, tokens.errorBg, 4.5],
    ["primary button text", tokens.actionText, tokens.actionBg, 4.5],
  ];

  assert.equal(tokens.focus.toLowerCase(), "#f4b41a", `${theme.name} theme must use the approved gold focus accent`);
  if (theme.name === "dark") {
    assert.equal(tokens.surface.toLowerCase(), "#000000", "Dark primary surface must be black");
    assert.equal(tokens.raised.toLowerCase(), "#0a0a0a", "Dark raised surface must be near-black");
    assert.equal(tokens.accent.toLowerCase(), "#f4b41a", "Dark accent text must use gold");
    assert.equal(tokens.actionBg.toLowerCase(), "#f4b41a", "Dark primary action must use gold fill");
    assert.equal(tokens.actionText.toLowerCase(), "#000000", "Dark primary action text must be black");
  } else {
    assert.equal(tokens.surface.toLowerCase(), "#ffffff", "Light primary surface must be white");
    assert.equal(tokens.accent.toLowerCase(), "#1a4d5c", "Light accent text must use teal");
    assert.equal(tokens.actionBg.toLowerCase(), "#1a4d5c", "Light primary action must use teal fill");
    assert.equal(tokens.actionText.toLowerCase(), "#ffffff", "Light primary action text must be white");
  }

  for (const [label, foreground, background, minimum] of checks) {
    const ratio = contrast(foreground, background);
    assert.ok(ratio >= minimum, `${theme.name} theme: ${label} contrast ${ratio.toFixed(2)}:1 is below ${minimum}:1`);
    console.log(`PASS ${theme.name}: ${label} ${ratio.toFixed(2)}:1`);
  }
}

for (const [selector, minimum] of [
  [".nav-item", 13],
  ["label", 12],
  [".row.header", 12],
  [".metric-card span", 12],
  ["kbd", 12],
  [".app-statusbar", 11],
]) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const rule = [...css.matchAll(new RegExp(`(?:^|\\n)\\s*${escaped}\\s*\\{([^}]*)\\}`, "g"))].at(-1)?.[1];
  assert.ok(rule, `Missing readable-size rule for ${selector}`);
  const size = rule.match(/font-size:\s*(\d+(?:\.\d+)?)px/i);
  assert.ok(size && Number(size[1]) >= minimum, `${selector} must be at least ${minimum}px`);
  console.log(`PASS font size: ${selector} ${size[1]}px`);
}

console.log("Accessibility token and minimum-size checks passed. Manual rendered UI review is still required.");
