import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.jsx";
import {
  farmApi, batchApi, expenseApi, customerApi, salesApi, inventoryApi,
  feedApi, pricingApi, dashboardApi, attentionApi, auditApi,
  eggApi, broilerApi,
} from "./services/api.js";

const farm = {
  id: "farm-1",
  name: "Grantino Farms",
  location: "Port Harcourt",
  timezone: "Africa/Lagos",
  currency: "NGN",
};

const settings = {
  defaultCrateSize: 30,
  defaultWaterContainerSize: 25,
  waterContainerSizes: [25, 75],
};

const batches = [
  {
    id: "layer-1",
    code: "L-2026-001",
    type: "LAYER",
    placementDate: "2026-10-01",
    initialBirdCount: 3000,
    status: "ACTIVE",
  },
  {
    id: "broiler-1",
    code: "B-2026-001",
    type: "BROILER",
    placementDate: "2026-10-01",
    initialBirdCount: 2000,
    status: "ACTIVE",
  },
];

function mockFarmApis({
  currentBatches = [],
  dashboardValue = {
    asOf: "2026-10-03",
    totalBirds: 5000,
    layerBirds: 3000,
    broilerBirds: 2000,
    activeBatches: 2,
    mortality: 83,
    eggsGood: 875,
    revenueMinor: 100000,
    expensesMinor: 70000,
    profitMinor: 30000,
    inventoryAlerts: 1,
    feedSummary: {
      typeCount: 2,
      remainingQuantityMilli: 12500,
      remainingCostMinor: 50000,
      consumedCostMinor: 25000,
    },
  },
} = {}) {
  vi.spyOn(farmApi, "get").mockResolvedValue(farm);
  vi.spyOn(farmApi, "getSettings").mockResolvedValue(settings);
  vi.spyOn(farmApi, "listHouses").mockResolvedValue([]);
  vi.spyOn(batchApi, "list").mockResolvedValue(currentBatches);
  vi.spyOn(expenseApi, "list").mockResolvedValue([]);
  vi.spyOn(customerApi, "list").mockResolvedValue([]);
  vi.spyOn(salesApi, "list").mockResolvedValue([]);
  vi.spyOn(inventoryApi, "listItems").mockResolvedValue([]);
  vi.spyOn(feedApi, "listTypes").mockResolvedValue([]);
  vi.spyOn(feedApi, "inventory").mockResolvedValue([]);
  vi.spyOn(pricingApi, "settings").mockResolvedValue({
    targetMarginPercent: null,
    workingMarginPercent: null,
  });
  vi.spyOn(dashboardApi, "farm").mockResolvedValue(dashboardValue);
  vi.spyOn(dashboardApi, "batch").mockResolvedValue({
    batch: { type: "LAYER", status: "ACTIVE" },
    population: {
      initialBirds: 3000,
      currentBirds: 2980,
      mortality: 20,
      culling: 0,
      sold: 0,
    },
    feed: { totalQuantityMilli: 10000, totalCostMinor: 50000 },
    expensesMinor: 10000,
    revenueMinor: 0,
    profitMinor: -10000,
    eggInventory: { goodCollected: 100, cracked: 2, goodRemaining: 100 },
    eggQuality: { goodRatePercent: 98 },
    attention: 0,
    broilerGrowth: null,
    broilerSales: [],
  });
  vi.spyOn(attentionApi, "list").mockResolvedValue([]);
  vi.spyOn(auditApi, "list").mockResolvedValue([]);
  vi.spyOn(eggApi, "listCollections").mockResolvedValue([]);
  vi.spyOn(eggApi, "listSales").mockResolvedValue([]);
  vi.spyOn(eggApi, "inventory").mockResolvedValue({
    goodCollected: 100,
    goodSold: 0,
    goodRemaining: 100,
    crackedCollected: 2,
    totalCollected: 102,
  });
  vi.spyOn(broilerApi, "weights").mockResolvedValue([]);
  vi.spyOn(broilerApi, "growth").mockResolvedValue({
    averageWeightKg: 1.2,
    weightGainKg: 0.4,
    fcr: 2,
    trend: [],
  });
  vi.spyOn(broilerApi, "sales").mockResolvedValue([]);
}

afterEach(() => vi.restoreAllMocks());

beforeEach(() => {
  localStorage.clear();
  document.body.innerHTML = "";
});

describe("App", () => {
  it("shows a loading state before farm data resolves", async () => {
    let resolveFarm;
    vi.spyOn(farmApi, "get").mockImplementation(
      () => new Promise(resolve => { resolveFarm = resolve; })
    );

    render(<App />);
    expect(screen.getByText("Loading farm...")).toBeInTheDocument();

    vi.spyOn(farmApi, "getSettings").mockResolvedValue(settings);
    vi.spyOn(farmApi, "listHouses").mockResolvedValue([]);
    vi.spyOn(batchApi, "list").mockResolvedValue([]);
    vi.spyOn(expenseApi, "list").mockResolvedValue([]);
    vi.spyOn(customerApi, "list").mockResolvedValue([]);
    vi.spyOn(salesApi, "list").mockResolvedValue([]);
    vi.spyOn(inventoryApi, "listItems").mockResolvedValue([]);
    vi.spyOn(feedApi, "listTypes").mockResolvedValue([]);
    vi.spyOn(feedApi, "inventory").mockResolvedValue([]);
    vi.spyOn(pricingApi, "settings").mockResolvedValue({});
    vi.spyOn(dashboardApi, "farm").mockResolvedValue(null);
    vi.spyOn(attentionApi, "list").mockResolvedValue([]);
    vi.spyOn(auditApi, "list").mockResolvedValue([]);

    resolveFarm(farm);
    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
  });

  it("renders the farm setup screen when no farm exists", async () => {
    vi.spyOn(farmApi, "get").mockRejectedValue(new Error("No farm has been created yet."));

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: /set up your farm/i })).toBeInTheDocument());
    expect(screen.getByText("GRANTINO FARMS")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create farm" })).toBeInTheDocument();
  });

  it("blocks an invalid setup form before calling the API", async () => {
    vi.spyOn(farmApi, "get").mockRejectedValue(new Error("No farm has been created yet."));
    const createFarm = vi.spyOn(farmApi, "create").mockResolvedValue(farm);

    render(<App />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /set up your farm/i })).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Create farm" }));

    expect(createFarm).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Farm name")).toBeInvalid();
    expect(screen.getByLabelText("Timezone")).toBeValid();
    expect(screen.getByLabelText("Currency")).toBeValid();
  });

  it("displays structured API validation errors", async () => {
    vi.spyOn(farmApi, "get").mockRejectedValue(new Error("No farm has been created yet."));
    vi.spyOn(farmApi, "create").mockRejectedValue(Object.assign(
      new Error("Validation failed"),
      { details: { name: "Farm name already exists", currency: "Currency must be 3 letters" } }
    ));

    render(<App />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /set up your farm/i })).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText("Farm name"), { target: { value: "Grantino Farms" } });
    fireEvent.change(screen.getByLabelText("Timezone"), { target: { value: "Africa/Lagos" } });
    fireEvent.change(screen.getByLabelText("Currency"), { target: { value: "NGN" } });
    fireEvent.click(screen.getByRole("button", { name: "Create farm" }));

    await waitFor(() => expect(screen.getByText(/name: Farm name already exists/)).toBeInTheDocument());
    expect(screen.getByText(/currency: Currency must be 3 letters/)).toBeInTheDocument();
  });

  it("renders backend-derived dashboard metrics and empty dashboard states", async () => {
    mockFarmApis({ currentBatches: [] });

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("5,000")).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("₦1,000.00")).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("₦300.00")).toBeInTheDocument());
    expect(screen.getByText("Nothing currently requires attention.")).toBeInTheDocument();
    expect(screen.getByText("No audit records yet.")).toBeInTheDocument();
    expect(screen.getByText("No batches yet.")).toBeInTheDocument();
    expect(screen.getByText("No customers yet.")).toBeInTheDocument();
    expect(screen.getByText("No expenses recorded.")).toBeInTheDocument();
  });

  it("renders an em dash for undefined backend metrics", async () => {
    mockFarmApis({
      dashboardValue: {
        asOf: "2026-10-03",
        totalBirds: 5000,
        layerBirds: 3000,
        broilerBirds: 2000,
        activeBatches: 2,
        mortality: 83,
        eggsGood: 875,
        revenueMinor: null,
        expensesMinor: 70000,
        profitMinor: null,
        inventoryAlerts: 0,
        feedSummary: {
          typeCount: 0,
          remainingQuantityMilli: null,
          remainingCostMinor: null,
          consumedCostMinor: null,
        },
      },
    });

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getAllByText("—").length).toBeGreaterThan(0));
  });

  it("shows a backend error state when dashboard loading fails", async () => {
    mockFarmApis();
    vi.mocked(dashboardApi.farm).mockRejectedValue(new Error("Dashboard service unavailable"));

    render(<App />);

    await waitFor(() => expect(screen.getByText("Dashboard service unavailable")).toBeInTheDocument());
  });

  it("exposes layer and broiler features for the matching batch type", async () => {
    mockFarmApis({ currentBatches: batches });

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());

    const eggBatch = screen.getByRole("option", { name: "Select a layer batch" }).parentElement;
    fireEvent.change(eggBatch, { target: { value: "layer-1" } });
    await waitFor(() => expect(screen.getByRole("heading", { name: "Record egg production" })).toBeInTheDocument());
    expect(within(eggBatch).getByRole("option", { name: "L-2026-001" })).toBeInTheDocument();

    const broilerBatch = screen.getByRole("option", { name: "Select a broiler batch" }).parentElement;
    fireEvent.change(broilerBatch, { target: { value: "broiler-1" } });
    await waitFor(() => expect(screen.getByRole("heading", { name: "Record weight" })).toBeInTheDocument());
    expect(within(broilerBatch).getByRole("option", { name: /B-2026-001/ })).toBeInTheDocument();
  });

  it("switches the batch dashboard and requests the selected batch", async () => {
    mockFarmApis({ currentBatches: batches });
    vi.mocked(dashboardApi.batch).mockImplementation(async id => ({
      batch: { type: id === "layer-1" ? "LAYER" : "BROILER", status: "ACTIVE" },
      population: { initialBirds: id === "layer-1" ? 3000 : 2000, currentBirds: 2990, mortality: 10, culling: 0, sold: 0 },
      feed: { totalQuantityMilli: 10000, totalCostMinor: 50000 },
      expensesMinor: 10000,
      revenueMinor: 0,
      profitMinor: -10000,
      eggInventory: id === "layer-1" ? { goodCollected: 100, cracked: 2, goodRemaining: 100 } : null,
      eggQuality: id === "layer-1" ? { goodRatePercent: 98 } : null,
      attention: 0,
      broilerGrowth: id === "broiler-1" ? { currentAverageWeightKg: 1.2, liveWeightGainKg: 0.4, fcr: 2 } : null,
      broilerSales: [],
    }));

    render(<App />);

    await waitFor(() => expect(screen.getByRole("combobox", { name: "Batch dashboard" })).toBeInTheDocument());

    fireEvent.change(screen.getByRole("combobox", { name: "Batch dashboard" }), { target: { value: "broiler-1" } });

    await waitFor(() => expect(dashboardApi.batch).toHaveBeenLastCalledWith("broiler-1"));
    expect(screen.getByText("BROILER")).toBeInTheDocument();
    expect(localStorage.getItem("grantino:lastBatchId")).toBe("broiler-1");
  });
});
