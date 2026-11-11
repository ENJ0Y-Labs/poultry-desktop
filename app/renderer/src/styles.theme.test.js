import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const stylesheet = readFileSync(
  resolve(process.cwd(), "app/renderer/src/styles.css"),
  "utf8"
);

describe("navigation theme tokens", () => {
  it("defines light-theme values for every IDE shell surface and interaction token", () => {
    const lightTheme = stylesheet.match(/:root\[data-theme="light"\]\s*\{([^}]*)\}/g)?.join("\n") ?? "";

    for (const token of [
      "--ide-titlebar",
      "--ide-sidebar",
      "--ide-workspace",
      "--ide-card",
      "--ide-hover",
      "--ide-border",
      "--ide-accent",
      "--ide-text",
      "--ide-muted",
      "--ide-tab-bg",
      "--ide-tab-hover-bg",
      "--ide-tab-active-bg",
      "--ide-tab-active-border",
      "--ide-scrollbar",
      "--ide-scrollbar-hover"
    ]) {
      expect(lightTheme, `missing light-theme value for ${token}`).toContain(token);
    }
  });

  it("defines theme-aware tokens for landing, sign-in, and first-run screens", () => {
    const brandTokensStart = stylesheet.indexOf("/* Pomas brand system: semantic overrides");
    const darkStart = stylesheet.indexOf(":root {", brandTokensStart);
    const darkEnd = stylesheet.indexOf("\n}", darkStart);
    const lightStart = stylesheet.indexOf(':root[data-theme="light"] {', darkEnd);
    const lightEnd = stylesheet.indexOf("\n}", lightStart);
    const darkTheme = stylesheet.slice(darkStart, darkEnd);
    const lightTheme = stylesheet.slice(lightStart, lightEnd);

    for (const token of [
      "--auth-story-background", "--auth-story-text", "--auth-story-muted",
      "--auth-story-border", "--auth-feature-background", "--auth-accent",
      "--auth-button-background", "--auth-button-text", "--auth-focus-ring"
    ]) {
      expect(darkTheme, "missing dark/default value for " + token).toContain(token);
      expect(lightTheme, "missing light value for " + token).toContain(token);
    }
    expect(stylesheet).toContain("background:var(--auth-story-background)");
    expect(stylesheet).toContain("background:var(--auth-button-background)");
  });

  it("uses semantic theme tokens for tabs and focus", () => {
    expect(stylesheet).toMatch(/\.page-tabs\s*\{[^}]*border:\s*1px solid var\(--border\);[^}]*background:\s*var\(--ide-tab-bg\)/);
    expect(stylesheet).toMatch(/\.page-tab:hover\s*\{[^}]*color:\s*var\(--text\);[^}]*background:\s*var\(--ide-tab-hover-bg\)/);
    expect(stylesheet).toMatch(/\.page-tab\.active\s*\{[^}]*border-color:\s*var\(--ide-tab-active-border\);[^}]*background:\s*var\(--ide-tab-active-bg\)/);
  });

  it("aligns the single sidebar and main content in expanded and collapsed modes", () => {
    expect(stylesheet).toMatch(/\.sidebar\s*\{\s*inset:\s*0 auto 0 0;\s*width:\s*var\(--ide-sidebar-width\);/);
    expect(stylesheet).toContain(".app-main { margin-left: var(--ide-sidebar-width); }");
    expect(stylesheet).toContain(".app-shell.sidebar-collapsed .sidebar { width: 68px; }");
    expect(stylesheet).toContain(".app-shell.sidebar-collapsed .app-main { margin-left: 68px; }");
    expect(stylesheet).not.toContain("activity-bar");
    expect(stylesheet).not.toContain("--ide-activity-width");
  });

  it("keeps navigation icons at 20px with a larger click target", () => {
    expect(stylesheet).toMatch(/\.nav-glyph\s*\{[^}]*width:\s*20px;[^}]*height:\s*20px;/);
    expect(stylesheet).toMatch(/\.nav-item\s*\{\s*min-height:\s*40px;/);
    expect(stylesheet).toContain(".app-shell.sidebar-collapsed .sidebar .nav-item { justify-content: center;");
  });

  it("keeps the separate sidebar collapse control above the brand at a 40px target", () => {
    expect(stylesheet).toMatch(/\.sidebar-toggle\s*\{[^}]*z-index:\s*1;[^}]*flex:\s*0 0 40px;[^}]*width:\s*40px;[^}]*height:\s*40px;/s);
    expect(stylesheet).toContain("--ide-sidebar-header-height: 48px;");
  });

  it("centers the application logo inside the collapsed expand target", () => {
    expect(stylesheet).toContain(".brand-mark-button img { width: 26px; height: 26px;");
    expect(stylesheet).toContain(".app-shell.sidebar-collapsed .sidebar .brand-mark-button");
    expect(stylesheet).toMatch(/\.app-shell\.sidebar-collapsed \.sidebar \.brand-mark-button\s*\{[^}]*width:\s*40px;[^}]*height:\s*40px;/);
    expect(stylesheet).toMatch(/\.brand-mark-button\s*\{[^}]*border:\s*0;[^}]*border-radius:\s*0;[^}]*background:\s*transparent;/);
    expect(stylesheet).toContain(".brand-mark-button:hover:not(:disabled) .brand-logo");
    expect(stylesheet).toContain(".brand-mark-button:hover:not(:disabled) .brand-hover-icon");
    expect(stylesheet).toMatch(/\.brand-mark-button \.brand-logo,\s*\.brand-mark-button \.brand-hover-icon\s*\{\s*grid-area:\s*1 \/ 1;/);
    expect(stylesheet).toMatch(/\.brand-mark-button:focus-visible\s*\{[^}]*outline:\s*2px solid var\(--text\)/);
  });

  it("styles accessible switches with Pomas theme colors and visible interaction states", () => {
    expect(stylesheet).toMatch(/\.toggle-switch\s*\{[^}]*min-height:\s*40px;[^}]*border-radius:\s*8px;/s);
    expect(stylesheet).toContain(".toggle-switch-input:checked + .toggle-switch-track");
    expect(stylesheet).toMatch(/\.toggle-switch-input:focus-visible \+ \.toggle-switch-track\s*\{[^}]*outline:\s*2px solid var\(--ide-accent\)/);
    expect(stylesheet).toContain(".toggle-switch.is-disabled");
    expect(stylesheet).toMatch(/\.toggle-switch-thumb\s*\{[^}]*transition:\s*transform \.16s ease/);
    expect(stylesheet).toContain(".theme-switches .toggle-switch");
    expect(stylesheet).toMatch(/\.pomas-page fieldset\.field > label\s*\{[^}]*display:\s*flex/);
    expect(stylesheet).toMatch(/input\[type="checkbox"\]:not\(\.toggle-switch-input\)\s*\{[^}]*width:\s*16px;[^}]*height:\s*16px;[^}]*min-height:\s*16px;/s);
  });
});


describe("dashboard attention theme contrast", () => {
  it("keeps alert cards on semantic surfaces instead of inheriting primary-button gold", () => {
    const accessibility = readFileSync(
      resolve(process.cwd(), "app/renderer/src/accessibility.css"),
      "utf8"
    );

    expect(accessibility).toMatch(/button:not\([^\n]*:not\(\.attention-item\)/);
    expect(accessibility).toMatch(/\.attention-item\s*\{[^}]*background:\s*var\(--pomas-panel-raised\)[^}]*color:\s*var\(--a11y-text\)/s);
    expect(accessibility).toMatch(/\.attention-item-clickable:hover,[\s\S]*?background:\s*var\(--pomas-panel-bg\)/);
    expect(accessibility).toMatch(/\.severity\.warning\s*\{[^}]*background:\s*var\(--a11y-warning-bg\)[^}]*color:\s*var\(--a11y-warning-text\)/s);
    expect(accessibility).toMatch(/\.severity\.critical\s*\{[^}]*background:\s*var\(--a11y-critical-bg\)[^}]*color:\s*var\(--a11y-critical-text\)/s);
    expect(accessibility).toMatch(/\.severity\.error\s*\{[^}]*background:\s*var\(--a11y-error-bg\)[^}]*color:\s*var\(--a11y-error-text\)/s);
  });

  it("keeps the collapsed brand logo out of primary-button background styling", () => {
    const accessibility = readFileSync(
      resolve(process.cwd(), "app/renderer/src/accessibility.css"),
      "utf8"
    );

    expect(accessibility).toMatch(/button:not\([^\n]*:not\(\.brand-mark-button\)/);
    expect(accessibility).toMatch(/button:not\([^\n]*:not\(\.brand-mark-button\)[^\n]*:hover/);
  });
});
