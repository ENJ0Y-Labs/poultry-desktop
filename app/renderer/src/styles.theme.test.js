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
      "--ide-activity",
      "--ide-sidebar",
      "--ide-workspace",
      "--ide-card",
      "--ide-hover",
      "--ide-border",
      "--ide-accent",
      "--ide-text",
      "--ide-muted",
      "--ide-focus-ring",
      "--ide-tab-bg",
      "--ide-tab-hover-bg",
      "--ide-tab-active-bg",
      "--ide-tab-active-border",
      "--ide-activity-icon",
      "--ide-activity-active-bg",
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

  it("uses semantic theme tokens for activity selection, tabs, hover, and focus", () => {
    expect(stylesheet).toMatch(/\.activity-button\.active\s*\{[^}]*background:\s*var\(--ide-activity-active-bg\)/);
    expect(stylesheet).toMatch(/\.activity-button:focus-visible\s*\{[^}]*outline:\s*2px solid var\(--ide-focus-ring\)/);
    expect(stylesheet).toMatch(/\.page-tabs\s*\{[^}]*border:\s*1px solid var\(--border\);[^}]*background:\s*var\(--ide-tab-bg\)/);
    expect(stylesheet).toMatch(/\.page-tab:hover\s*\{[^}]*color:\s*var\(--text\);[^}]*background:\s*var\(--ide-tab-hover-bg\)/);
    expect(stylesheet).toMatch(/\.page-tab\.active\s*\{[^}]*border-color:\s*var\(--ide-tab-active-border\);[^}]*background:\s*var\(--ide-tab-active-bg\)/);
  });

  it("aligns the single sidebar and main content in expanded and collapsed modes", () => {
    expect(stylesheet).toMatch(/\.sidebar\s*\{\s*inset:\s*0 auto 0 0;\s*width:\s*var\(--ide-sidebar-width\);/);
    expect(stylesheet).toContain(".app-main { margin-left: var(--ide-sidebar-width); }");
    expect(stylesheet).toContain(".app-shell.sidebar-collapsed .sidebar { width: 68px; }");
    expect(stylesheet).toContain(".app-shell.sidebar-collapsed .app-main { margin-left: 68px; }");
    expect(stylesheet).not.toContain("calc(var(--ide-activity-width) + var(--ide-sidebar-width))");
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
});
