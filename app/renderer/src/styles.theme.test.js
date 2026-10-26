import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const stylesheet = readFileSync(new URL("./styles.css", import.meta.url), "utf8");

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

  it("uses semantic theme tokens for activity selection, tabs, hover, and focus", () => {
    expect(stylesheet).toContain(".activity-button.active { background: var(--ide-activity-active-bg);");
    expect(stylesheet).toContain(".activity-button:focus-visible { outline-color: var(--ide-focus-ring); }");
    expect(stylesheet).toContain(".page-tabs { border-color: var(--border); background: var(--ide-tab-bg); }");
    expect(stylesheet).toContain(".page-tab:hover { color: var(--text); background: var(--ide-tab-hover-bg); }");
    expect(stylesheet).toContain(".page-tab.active { color: var(--text); border-color: var(--ide-tab-active-border); background: var(--ide-tab-active-bg); }");
  });
});
