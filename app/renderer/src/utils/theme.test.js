import { afterEach, describe, expect, it, vi } from "vitest";
import { applyTheme, getThemePreference, initializeTheme, setThemePreference } from "./theme.js";

afterEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
  delete document.documentElement.dataset.themePreference;
  vi.restoreAllMocks();
});

describe("theme preference", () => {
  it("defaults to Auto and applies the system preference before render", () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    expect(getThemePreference()).toBe("auto");
    initializeTheme();
    expect(document.documentElement.dataset.theme).toBe("light");
  });

  it("persists and applies Light and Dark choices", () => {
    setThemePreference("light");
    expect(localStorage.getItem("pomas:theme")).toBe("light");
    expect(document.documentElement.dataset.theme).toBe("light");
    setThemePreference("dark");
    expect(localStorage.getItem("pomas:theme")).toBe("dark");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });

  it("rejects invalid preferences and ignores invalid stored values", () => {
    localStorage.setItem("pomas:theme", "sepia");
    expect(getThemePreference()).toBe("auto");
    expect(() => setThemePreference("sepia")).toThrow("Theme must be auto, light, or dark.");
    applyTheme("invalid");
    expect(document.documentElement.dataset.themePreference).toBe("auto");
  });
});
