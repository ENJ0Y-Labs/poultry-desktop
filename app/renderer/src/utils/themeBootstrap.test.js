import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { describe, expect, it, vi } from "vitest";

const bootstrap = readFileSync(
  resolve(process.cwd(), "app/renderer/public/theme-init.js"),
  "utf8"
);

function runBootstrap({ saved = null, legacy = null, prefersLight = false, storageThrows = false } = {}) {
  const root = { dataset: {} };
  const localStorage = {
    getItem: vi.fn((key) => {
      if (storageThrows) throw new Error("Storage unavailable");
      if (key === "pomas:theme") return saved;
      if (key === "grantino:theme") return legacy;
      return null;
    }),
  };

  runInNewContext(bootstrap, {
    document: { documentElement: root },
    localStorage,
    window: { matchMedia: vi.fn(() => ({ matches: prefersLight })) },
  });

  return root.dataset;
}

describe("pre-paint theme bootstrap", () => {
  it("applies a saved Light preference before renderer startup", () => {
    expect(runBootstrap({ saved: "light" })).toEqual({
      themePreference: "light",
      theme: "light",
    });
  });

  it("applies a saved Dark preference even when the system prefers Light", () => {
    expect(runBootstrap({ saved: "dark", prefersLight: true })).toEqual({
      themePreference: "dark",
      theme: "dark",
    });
  });

  it("uses the operating-system preference when Auto is selected", () => {
    expect(runBootstrap({ prefersLight: true })).toEqual({
      themePreference: "auto",
      theme: "light",
    });
    expect(runBootstrap({ prefersLight: false })).toEqual({
      themePreference: "auto",
      theme: "dark",
    });
  });

  it("continues to read the legacy saved preference", () => {
    expect(runBootstrap({ legacy: "light" })).toEqual({
      themePreference: "light",
      theme: "light",
    });
  });

  it("keeps Auto and follows the system when local storage is unavailable", () => {
    expect(runBootstrap({ storageThrows: true, prefersLight: true })).toEqual({
      themePreference: "auto",
      theme: "light",
    });
  });
});
