import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, afterEach } from "vitest";
import App from "./App.jsx";
import {
  farmApi, batchApi, expenseApi, customerApi, salesApi, inventoryApi,
  feedApi, pricingApi, dashboardApi, attentionApi, auditApi
} from "./services/api.js";

afterEach(() => vi.restoreAllMocks());

describe("App", () => {
  it("renders the farm setup screen when no farm exists", async () => {
    vi.spyOn(farmApi, "get").mockRejectedValue(new Error("No farm has been created yet."));
    render(<App />);
    expect(screen.getByText("Loading farm...")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("heading", { name: /set up your farm/i })).toBeInTheDocument());
    expect(screen.getByText("GRANTINO FARMS")).toBeInTheDocument();
  });

  it("renders backend-derived farm dashboard metrics", async () => {
    vi.spyOn(farmApi, "get").mockResolvedValue({ id:"farm-1", name:"Grantino Farms", location:"Port Harcourt", timezone:"Africa/Lagos", currency:"NGN" });
    vi.spyOn(farmApi, "getSettings").mockResolvedValue({ defaultCrateSize:30, defaultWaterContainerSize:25, waterContainerSizes:[25,75] });
    vi.spyOn(farmApi, "listHouses").mockResolvedValue([]);
    vi.spyOn(batchApi, "list").mockResolvedValue([]);
    vi.spyOn(expenseApi, "list").mockResolvedValue([]);
    vi.spyOn(customerApi, "list").mockResolvedValue([]);
    vi.spyOn(salesApi, "list").mockResolvedValue([]);
    vi.spyOn(inventoryApi, "listItems").mockResolvedValue([]);
    vi.spyOn(feedApi, "listTypes").mockResolvedValue([]);
    vi.spyOn(feedApi, "inventory").mockResolvedValue([]);
    vi.spyOn(pricingApi, "settings").mockResolvedValue({ targetMarginPercent:null, workingMarginPercent:null });
    vi.spyOn(dashboardApi, "farm").mockResolvedValue({
      asOf:"2026-10-03", totalBirds:5000, layerBirds:3000, broilerBirds:2000, activeBatches:2,
      mortality:83, eggsGood:875, revenueMinor:100000, expensesMinor:70000, profitMinor:30000,
      inventoryAlerts:1, feedSummary:{ typeCount:2, remainingQuantityMilli:12500, remainingCostMinor:50000, consumedCostMinor:25000 }
    });
    vi.spyOn(attentionApi, "list").mockResolvedValue([]);
    vi.spyOn(auditApi, "list").mockResolvedValue([]);

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name:"Farm dashboard" })).toBeInTheDocument());
    expect(screen.getByText("5,000")).toBeInTheDocument();
    expect(screen.getByText("₦1,000.00")).toBeInTheDocument();
    expect(screen.getByText("₦300.00")).toBeInTheDocument();
    expect(screen.getByText("2 types")).not.toBeInTheDocument();
  });
});
