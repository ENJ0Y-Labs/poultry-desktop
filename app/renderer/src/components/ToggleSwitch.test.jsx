import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ThemePreferenceSwitches from "./ThemePreferenceSwitches.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ToggleSwitch from "./ToggleSwitch.jsx";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("ToggleSwitch", () => {
  it("exposes a labeled, controlled switch and reports changes", () => {
    const onChange = vi.fn();
    render(<ToggleSwitch label="Automatic backups" checked={false} onChange={onChange} />);

    const toggle = screen.getByRole("switch", { name: "Automatic backups" });
    expect(toggle).not.toBeChecked();
    fireEvent.click(toggle);
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it("keeps a disabled switch unavailable to interaction", () => {
    const onChange = vi.fn();
    render(<ToggleSwitch label="Dark appearance" checked disabled onChange={onChange} />);

    const toggle = screen.getByRole("switch", { name: "Dark appearance" });
    expect(toggle).toBeChecked();
    expect(toggle).toBeDisabled();
    fireEvent.click(toggle);
    expect(onChange).not.toHaveBeenCalled();
  });
});

describe("ThemePreferenceSwitches", () => {
  it("keeps Auto distinct from the explicit Light and Dark preferences", () => {
    const onChange = vi.fn();
    vi.stubGlobal("matchMedia", vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })));
    render(<ThemePreferenceSwitches value="auto" onChange={onChange} />);

    const system = screen.getByRole("switch", { name: "Follow system appearance" });
    const dark = screen.getByRole("switch", { name: "Dark appearance" });
    expect(system).toBeChecked();
    expect(dark).toBeChecked();
    expect(dark).toBeDisabled();

    fireEvent.click(system);
    expect(onChange).toHaveBeenCalledWith("dark");
  });
});
