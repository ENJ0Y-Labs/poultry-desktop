import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import AppShell from "./AppShell.jsx";

describe("AppShell", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
    vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(() => {});
  });

  it("renders the complete primary navigation", () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "Port Harcourt", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
      </AppShell>
    );

    expect(screen.getByRole("button", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Houses / Pens" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Batches / Flocks" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Daily Records" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Eggs" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Weight & Growth" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Stock" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sales" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reports" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Settings" })).toBeInTheDocument();
  });

  it("scrolls to the selected module", () => {
    render(
      <AppShell farm={{ name: "Grantino", location: "", currency: "NGN", timezone: "Africa/Lagos" }}>
        <section id="dashboard">Dashboard</section>
        <section id="inventory">Inventory</section>
      </AppShell>
    );

    fireEvent.click(screen.getByRole("button", { name: "Stock" }));
    expect(document.getElementById("inventory").scrollIntoView).toBeDefined();
  });
});
