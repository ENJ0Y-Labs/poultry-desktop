import { render, screen, fireEvent, within, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import AppShell from "./AppShell.jsx";

describe("AppShell", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({ schema: "VALID" }) }));
    localStorage.removeItem("pomas:theme");
    localStorage.removeItem("grantino:theme");
    localStorage.removeItem("pomas:sidebar-collapsed");
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

    const navigation = screen.getByRole("navigation", { name: "Main navigation" });
    for (const label of [
      "Dashboard", "Flocks", "Daily Work", "Feed & Stock", "Health",
      "Production", "Sales & Money", "Reports", "Settings",
    ]) {
      expect(within(navigation).getByRole("button", { name: label })).toBeInTheDocument();
    }
  });

  it("uses the saved farm profile name in the shell", () => {
    render(
      <AppShell farm={{ name: "Riverbend Poultry", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    expect(screen.getAllByText("Riverbend Poultry").length).toBeGreaterThanOrEqual(2);
    expect(within(screen.getByRole("button", { name: "Account" })).getByText("RP")).toBeInTheDocument();
  });

  it("offers Auto, Light, and Dark theme preferences", () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );
    const selector = screen.getByRole("combobox", { name: "Theme" });
    expect(selector).toHaveValue("auto");
    expect(within(selector).getByRole("option", { name: "Teal Light" })).toBeInTheDocument();
    expect(within(selector).getByRole("option", { name: "Dark (Gold)" })).toBeInTheDocument();
    fireEvent.change(selector, { target: { value: "light" } });
    expect(localStorage.getItem("pomas:theme")).toBe("light");
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
  it("shows contextual page help with a useful next action", async () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    expect(screen.getByRole("region", { name: "Help for Farm overview" })).toHaveTextContent(/Farm overview/);
    fireEvent.click(screen.getByRole("button", { name: "Review flocks" }));
    expect(window.location.hash).toBe("#flocks/batch-dashboard");
    await waitFor(() => expect(screen.getByRole("region", { name: "Help for Flocks and housing" })).toHaveTextContent(/Flocks and housing/));
  });

  it("opens the guided spotlight tour from contextual page help", async () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    fireEvent.click(screen.getByRole("button", { name: "Start guided tour" }));
    expect(screen.getByRole("dialog", { name: "Welcome to Pomas" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(window.location.hash).toBe("#dashboard/overview"));
    expect(screen.getByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Skip tour" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows a retry action when the local backend is unavailable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      status: 503,
      ok: false,
      json: async () => ({ schema: "UNAVAILABLE" }),
    }));
    const onRetry = vi.fn();
    render(
      <AppShell
        farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}
        onRetry={onRetry}
      >
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    const retryButton = await screen.findByRole("button", { name: "Retry connection" });
    expect(screen.getByRole("alert")).toHaveTextContent(/local backend is unavailable/i);
    fireEvent.click(retryButton);
    await waitFor(() => expect(onRetry).toHaveBeenCalledTimes(1));
  });


  it("uses one navigation surface and persists collapse without changing the selected page", () => {
    const { unmount } = render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    expect(screen.getAllByRole("button", { name: "Flocks" })).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Flocks" }));
    expect(window.location.hash).toBe("#flocks/batch-dashboard");

    fireEvent.click(screen.getByRole("button", { name: "Collapse sidebar" }));
    expect(window.location.hash).toBe("#flocks/batch-dashboard");
    expect(localStorage.getItem("pomas:sidebar-collapsed")).toBe("true");
    expect(screen.getByRole("button", { name: "Expand sidebar" })).toHaveAttribute("aria-expanded", "false");

    unmount();
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    expect(screen.getByRole("button", { name: "Expand sidebar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Flocks" })).toHaveAttribute("aria-current", "page");
    expect(window.location.hash).toBe("#flocks/batch-dashboard");
  });

});
