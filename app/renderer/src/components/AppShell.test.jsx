import { render, screen, fireEvent, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import AppShell from "./AppShell.jsx";

describe("AppShell", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({ schema: "VALID" }) }));
    localStorage.removeItem("grantino:theme");
    window.location.hash = "";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders the nine primary pages", () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "Port Harcourt", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    for (const label of [
      "Dashboard", "Flocks", "Daily Work", "Feed & Stock", "Health",
      "Production", "Sales & Money", "Reports", "Settings",
    ]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
  });

  it("offers Auto, Light, and Dark theme preferences", () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );
    const selector = screen.getByRole("combobox", { name: "Theme" });
    expect(selector).toHaveValue("auto");
    expect(within(selector).getByRole("option", { name: "Light" })).toBeInTheDocument();
    expect(within(selector).getByRole("option", { name: "Dark" })).toBeInTheDocument();
    fireEvent.change(selector, { target: { value: "light" } });
    expect(localStorage.getItem("grantino:theme")).toBe("light");
    expect(document.documentElement.dataset.theme).toBe("light");
  });

  it("navigates to a page and tab through the URL hash", () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    fireEvent.click(screen.getByRole("button", { name: "Flocks" }));
    expect(window.location.hash).toBe("#flocks/batch-dashboard");

    fireEvent.click(screen.getByRole("button", { name: "Houses / pens" }));
    expect(window.location.hash).toBe("#flocks/houses");

    fireEvent.click(screen.getByRole("button", { name: "Daily Work" }));
    fireEvent.click(screen.getByRole("button", { name: "Water" }));
    expect(window.location.hash).toBe("#daily-work/water");

    fireEvent.click(screen.getByRole("button", { name: "Health" }));
    fireEvent.click(screen.getByRole("button", { name: "Drugs & treatments" }));
    expect(window.location.hash).toBe("#health/drugs");

    fireEvent.click(screen.getByRole("button", { name: "Feed & Stock" }));
    fireEvent.click(screen.getByRole("button", { name: "Suppliers" }));
    expect(window.location.hash).toBe("#feed-stock/suppliers");
  });
});
