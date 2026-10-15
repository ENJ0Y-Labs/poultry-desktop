import { render, screen, fireEvent, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
// eslint-disable-next-line no-unused-vars
import AppShell from "./AppShell.jsx";

describe("AppShell", () => {
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
  });t { render, screen, fireEvent, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
// eslint-disable-next-line no-unused-vars
import AppShell from "./AppShell.jsx";

describe("AppShell", () => {
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

  it("scrolls to the selected module", () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
        <section id="inventory">Inventory</section>
      </AppShell>
    );

    fireEvent.click(screen.getByRole("button", { name: "Stock" }));
    expect(document.getElementById("inventory").scrollIntoView).toHaveBeenCalled();
  });
});
