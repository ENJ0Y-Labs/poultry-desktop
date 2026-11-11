import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import DashboardKpiCard from "./DashboardKpiCard.jsx";

describe("DashboardKpiCard", () => {
  it("renders missing values as unavailable rather than zero", () => {
    render(<DashboardKpiCard label="Revenue" value={null} />);
    expect(screen.getByText("Revenue")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("renders a primary navigable KPI as a keyboard-accessible button", () => {
    render(<DashboardKpiCard label="Total birds" value="4,842" primary href="#flocks/batch-dashboard" description="View flock populations" />);
    const card = screen.getByRole("button", { name: "Total birds: 4,842. View flock populations" });
    expect(card).toHaveClass("dashboard-kpi-card-primary");
    fireEvent.click(card);
    expect(window.location.hash).toBe("#flocks/batch-dashboard");
  });

  it("does not invent a comparison when comparable period data is absent", () => {
    render(<DashboardKpiCard label="Good eggs" value="25 crates" comparison={{ value: "+5%" }} />);
    expect(screen.queryByText(/vs/)).not.toBeInTheDocument();
  });

  it("labels an explicitly supplied comparison with both periods", () => {
    render(<DashboardKpiCard label="Eggs today" value="420" comparison={{ value: "+5%", currentPeriod: "Today", previousPeriod: "Yesterday" }} />);
    expect(screen.getByText("+5% · Today vs Yesterday")).toBeInTheDocument();
  });
});
