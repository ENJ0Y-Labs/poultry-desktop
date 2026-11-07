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
});
