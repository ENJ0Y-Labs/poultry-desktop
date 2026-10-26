import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.jsx";
import {
  authApi, farmApi, batchApi, expenseApi, customerApi, salesApi, inventoryApi,
  feedApi, pricingApi, dashboardApi, attentionApi, auditApi,
  eggApi, broilerApi, reportApi, expenseCategoryApi, accountApi, settingsApi, supplierApi,
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
  waterUnitLabel: "litres",
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
  vi.spyOn(supplierApi, "list").mockResolvedValue([]);
  vi.spyOn(salesApi, "list").mockResolvedValue([]);
  vi.spyOn(inventoryApi, "listItems").mockResolvedValue([]);
  vi.spyOn(feedApi, "listTypes").mockResolvedValue([]);
  vi.spyOn(feedApi, "inventory").mockResolvedValue([]);
  vi.spyOn(expenseCategoryApi, "list").mockResolvedValue([
    { id: "cat-1", name: "OTHER", status: "ACTIVE" }
  ]);
  vi.spyOn(accountApi, "get").mockResolvedValue({
    id: "user-1", email: "owner@example.com", fullName: "Farm Owner", role: "OWNER"
  });
  vi.spyOn(settingsApi, "backup").mockResolvedValue({
    enabled: false, directory: "", intervalMs: 86400000
  });
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
  vi.spyOn(reportApi, "farm").mockResolvedValue({
    asOf: "2026-10-03",
    dashboard: dashboardValue,
    feed: [],
    eggProduction: [],
    broilerGrowth: [],
    inventory: [],
    sales: [],
    expenses: [],
  });
  vi.spyOn(reportApi, "batch").mockResolvedValue({
    asOf: "2026-10-03",
    population: {
      initialBirds: 3000,
      currentBirds: 2980,
      mortality: 20,
    },
    feed: { totalQuantityMilli: 10000, totalCostMinor: 50000 },
    costs: { expensesMinor: 10000 },
    revenueMinor: 0,
    profitMinor: -10000,
    production: {
      type: "LAYER",
      eggInventory: { goodCollected: 100, goodRemaining: 100 },
      eggQuality: { goodRatePercent: 98 },
    },
    health: { healthRecords: [], drugs: [], vaccinations: [] },
    sales: [],
  });
}

afterEach(() => vi.restoreAllMocks());

beforeEach(() => {
  localStorage.clear();
  window.location.hash = "";
  document.body.innerHTML = "";
  vi.spyOn(authApi, "status").mockResolvedValue({ authenticated: true, setupRequired: false });
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
    vi.spyOn(supplierApi, "list").mockResolvedValue([]);
    vi.spyOn(salesApi, "list").mockResolvedValue([]);
    vi.spyOn(inventoryApi, "listItems").mockResolvedValue([]);
    vi.spyOn(feedApi, "listTypes").mockResolvedValue([]);
    vi.spyOn(feedApi, "inventory").mockResolvedValue([]);
    vi.spyOn(pricingApi, "settings").mockResolvedValue({});
    vi.spyOn(dashboardApi, "farm").mockResolvedValue(null);
    vi.spyOn(attentionApi, "list").mockResolvedValue([]);
    vi.spyOn(auditApi, "list").mockResolvedValue([]);
    vi.spyOn(expenseCategoryApi, "list").mockResolvedValue([]);
    vi.spyOn(accountApi, "get").mockResolvedValue({
      id: "user-1", email: "owner@example.com", fullName: "Farm Owner", role: "OWNER"
    });
    vi.spyOn(settingsApi, "application").mockResolvedValue({
      startPage: "dashboard", dateFormat: "YYYY-MM-DD"
    });
    vi.spyOn(settingsApi, "backup").mockResolvedValue({
      enabled: false, directory: "", intervalMs: 86400000
    });

    await waitFor(() => expect(resolveFarm).toBeTypeOf("function"));
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

  it("prompts for sign-in before requesting protected account data", async () => {
    mockFarmApis();
    vi.mocked(authApi.status).mockResolvedValue({ authenticated: false, setupRequired: false });
    const accountRequest = vi.mocked(accountApi.get);

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(accountRequest).not.toHaveBeenCalled();
  });

  it("creates the initial owner account, signs in, then loads the farm", async () => {
    mockFarmApis();
    vi.mocked(authApi.status).mockResolvedValue({ authenticated: false, setupRequired: true });
    const setup = vi.spyOn(authApi, "setup").mockResolvedValue({});
    const login = vi.spyOn(authApi, "login").mockResolvedValue({});

    render(<App />);

    await screen.findByRole("heading", { name: "Create owner account" });
    fireEvent.change(screen.getByLabelText("Full name"), { target: { value: "Farm Owner" } });
    fireEvent.change(screen.getByLabelText(/Username/), { target: { value: "farm-owner" } });
    fireEvent.change(screen.getByLabelText("Email (optional)"), { target: { value: "owner@example.com" } });
    fireEvent.change(screen.getByLabelText(/Password/), { target: { value: "correct-horse-battery" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account and sign in" }));

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    expect(setup).toHaveBeenCalledWith({
      username: "farm-owner",
      email: "owner@example.com",
      fullName: "Farm Owner",
      password: "correct-horse-battery",
    });
    expect(login).toHaveBeenCalledWith({
      loginIdentifier: "farm-owner",
      password: "correct-horse-battery",
      rememberMe: false,
    });
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

  it("labels the dashboard as-of date and follows the saved date format", async () => {
    mockFarmApis();
    vi.spyOn(settingsApi, "application").mockResolvedValue({
      startPage: "dashboard",
      dateFormat: "DD/MM/YYYY",
    });

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("03/10/2026")).toBeInTheDocument());
    expect(screen.getByText("Dashboard as of")).toBeInTheDocument();

    const primaryMetric = screen.getByText("Total birds").closest(".metric-card");
    const secondaryMetric = screen.getByText("Layer birds").closest(".metric-card");
    expect(primaryMetric).toHaveClass("metric-card-primary");
    expect(secondaryMetric).not.toHaveClass("metric-card-primary");
  });

  it("formats expense history and inventory remaining cost as naira", async () => {
    mockFarmApis();
    vi.mocked(expenseApi.list).mockResolvedValue([
      {
        id: "expense-1",
        occurredDate: "2026-10-04",
        description: "Mock medicine expense",
        amountMinor: 1234,
        category: "OTHER",
        batchId: null,
      },
    ]);
    vi.mocked(feedApi.inventory).mockResolvedValue([
      {
        feedTypeId: "feed-1",
        feedTypeName: "Starter",
        unit: "bag",
        purchasedQuantityMilli: 2000,
        consumedQuantityMilli: 500,
        remainingQuantityMilli: 1500,
        remainingCostMinor: 5678,
      },
    ]);

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Sales & Money" }));
    fireEvent.click(screen.getByRole("button", { name: "Expenses" }));
    expect(await screen.findByText("Mock medicine expense")).toBeInTheDocument();
    expect(screen.getByText("₦12.34")).toBeInTheDocument();
    expect(screen.queryByText("1234")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Feed & Stock" }));
    fireEvent.click(screen.getByRole("button", { name: "Other stock" }));
    expect(await screen.findByText("₦56.78")).toBeInTheDocument();
    expect(screen.queryByText("5678")).not.toBeInTheDocument();
  });

  it("exposes the theme selector through its visible label and allows changing it", async () => {
    mockFarmApis();

    render(<App />);

    const themeSelector = await screen.findByRole("combobox", { name: "Theme" });
    expect(themeSelector).toHaveValue("auto");

    fireEvent.change(themeSelector, { target: { value: "dark" } });
    expect(themeSelector).toHaveValue("dark");
  });

  it("renders backend-derived dashboard metrics and empty dashboard states", async () => {
    mockFarmApis({ currentBatches: [] });

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getAllByText("5,000")).toHaveLength(1));
    expect(within(screen.getByText("Revenue").parentElement).getByText("₦1,000.00")).toBeInTheDocument();
    expect(within(screen.getByText("Expenses").parentElement).getByText("₦700.00")).toBeInTheDocument();
    expect(within(screen.getByText("Profit").parentElement).getByText("₦300.00")).toBeInTheDocument();
    expect(screen.getByText("Nothing currently requires attention.")).toBeInTheDocument();
    expect(screen.getByText("No audit records yet.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Sales & Money" }));
    expect(screen.getByText("No customers yet.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Expenses" }));
    expect(screen.getByText("No expenses recorded.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Flocks" }));
    fireEvent.click(screen.getByRole("button", { name: "Batches & bird costs" }));
    expect(screen.getByText("No batches yet.")).toBeInTheDocument();
  });

  it("refreshes dashboard metrics and attention after a relevant mutation event", async () => {
    mockFarmApis();
    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    const dashboardCallsBeforeMutation = vi.mocked(dashboardApi.farm).mock.calls.length;
    const attentionCallsBeforeMutation = vi.mocked(attentionApi.list).mock.calls.length;

    window.dispatchEvent(new CustomEvent("pomas:data-mutated", {
      detail: { path: "/batches/layer-1/mortality", method: "POST" },
    }));

    await waitFor(() => {
      expect(dashboardApi.farm).toHaveBeenCalledTimes(dashboardCallsBeforeMutation + 1);
      expect(attentionApi.list).toHaveBeenCalledTimes(attentionCallsBeforeMutation + 1);
    });
  });

  it("allows egg sales to use a walk-in buyer without selecting a saved customer", async () => {
    mockFarmApis({ currentBatches: batches });
    vi.mocked(customerApi.list).mockResolvedValue([
      { id: "customer-1", name: "Saved Buyer", phone: null, notes: null },
    ]);
    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    fireEvent.click(within(screen.getByRole("navigation", { name: "Main navigation" })).getByRole("button", { name: "Production" }));
    fireEvent.change(await screen.findByLabelText("Layer batch"), { target: { value: "layer-1" } });

    const savedCustomer = await screen.findByLabelText("Saved customer");
    expect(savedCustomer).not.toBeRequired();
    expect(await screen.findByLabelText("Walk-in customer name")).toBeRequired();

    fireEvent.change(savedCustomer, { target: { value: "customer-1" } });
    expect(savedCustomer).toHaveValue("customer-1");
    expect(screen.queryByLabelText("Walk-in customer name")).not.toBeInTheDocument();

    fireEvent.change(savedCustomer, { target: { value: "" } });
    const walkInName = await screen.findByLabelText("Walk-in customer name");
    expect(walkInName).toBeRequired();
    fireEvent.change(walkInName, { target: { value: "Walk-in Buyer" } });
    expect(walkInName).toHaveValue("Walk-in Buyer");
  });

  it("shows actionable setup guidance only when the farm is genuinely empty", async () => {
    mockFarmApis({
      currentBatches: [],
      dashboardValue: {
        asOf: "2026-10-26",
        totalBirds: 0,
        layerBirds: 0,
        broilerBirds: 0,
        activeBatches: 0,
        mortality: 0,
        eggsGood: 0,
        revenueMinor: 0,
        expensesMinor: 0,
        profitMinor: 0,
        inventoryAlerts: 0,
        feedSummary: {
          typeCount: 0,
          remainingQuantityMilli: 0,
          remainingCostMinor: 0,
          consumedCostMinor: 0,
        },
      },
    });

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Your farm is ready to be set up" })).toBeInTheDocument();
    expect(screen.getByText(/Nothing has been added automatically/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create house / pen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create batch" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Open feed & stock" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Record daily work" })).toBeInTheDocument();
    expect(screen.queryByText("Total birds")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Create house / pen" }));
    expect(window.location.hash).toBe("#flocks/houses");
  });

  it("keeps the normal dashboard when zero metrics belong to a farm with setup records", async () => {
    mockFarmApis({
      currentBatches: [],
      dashboardValue: {
        asOf: "2026-10-26",
        totalBirds: 0,
        layerBirds: 0,
        broilerBirds: 0,
        activeBatches: 0,
        mortality: 0,
        eggsGood: 0,
        revenueMinor: 0,
        expensesMinor: 0,
        profitMinor: 0,
        inventoryAlerts: 0,
        feedSummary: { typeCount: 0, remainingQuantityMilli: 0, remainingCostMinor: 0, consumedCostMinor: 0 },
      },
    });
    vi.spyOn(farmApi, "listHouses").mockResolvedValue([
      { id: "house-1", name: "House 1", code: "H1", status: "ACTIVE" },
    ]);

    render(<App />);

    expect(await screen.findByText("Total birds")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Your farm is ready to be set up" })).not.toBeInTheDocument();
  });

  it("renders readable audit timestamps and labels, including unknown values", async () => {
    mockFarmApis();
    vi.mocked(auditApi.list).mockResolvedValue([
      {
        id: "audit-farm-created",
        occurredAt: "2026-10-26T08:21:44.123456789Z",
        action: "CREATE",
        entityType: "FARM",
        reason: null,
      },
      {
        id: "audit-unknown",
        occurredAt: "not-a-timestamp",
        action: "UNRECOGNIZED_ACTION",
        entityType: "NEW_ENTITY_KIND",
        reason: " ",
      },
    ]);

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Recent audit activity" })).toBeInTheDocument());
    expect(screen.getByText("26 Oct 2026, 09:21:44")).toBeInTheDocument();
    expect(screen.getByText("Created")).toBeInTheDocument();
    expect(screen.getByText("Farm")).toBeInTheDocument();
    expect(screen.getByText("Unknown time")).toBeInTheDocument();
    expect(screen.getByText("Unrecognized Action")).toBeInTheDocument();
    expect(screen.getByText("New Entity Kind")).toBeInTheDocument();
    const unknownAuditRow = screen.getByText("Unrecognized Action").closest(".row");
    expect(unknownAuditRow).not.toBeNull();
    expect(within(unknownAuditRow).getByText("—")).toBeInTheDocument();
  });

  it("renders backend-derived attention items on the dashboard", async () => {
    mockFarmApis();
    vi.mocked(attentionApi.list).mockResolvedValue([{
      type: "LOW_FEED_STOCK",
      severity: "WARNING",
      title: "Low feed stock",
      message: "Layer Mash has about 2.0 days of stock."
    }]);

    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    expect(await screen.findByText("Low feed stock")).toBeInTheDocument();
    expect(screen.getByText("Layer Mash has about 2.0 days of stock.")).toBeInTheDocument();
  });

  it("opens a missing daily-record alert with batch and date prefilled", async () => {
    mockFarmApis({ currentBatches: batches });
    vi.mocked(attentionApi.list).mockResolvedValue([{
      type: "MISSING_DAILY_RECORD",
      severity: "WARNING",
      title: "Missing daily record",
      message: "No daily record has been entered for L-2026-001 for 2026-10-25.",
      batchId: "layer-1",
      batchCode: "L-2026-001",
      date: "2026-10-25",
    }]);

    render(<App />);

    const alert = await screen.findByRole("button", { name: /Missing daily record.*Open related record/ });
    fireEvent.click(alert);

    await waitFor(() => expect(window.location.hash).toBe(
      "#daily-work/daily-record/batchId=layer-1/date=2026-10-25"
    ));
    expect(await screen.findByLabelText("Batch")).toHaveValue("layer-1");
    expect(screen.getByLabelText("Date")).toHaveValue("2026-10-25");
  });

  it("preserves a contextual batch when opening an alert route directly", async () => {
    mockFarmApis({ currentBatches: batches });
    window.location.hash = "#flocks/batch-dashboard/batchId=broiler-1";

    render(<App />);

    await waitFor(() => {
      expect(screen.getByLabelText("Batch dashboard")).toHaveValue("broiler-1");
    });
  });

  it("keeps alerts with missing required identifiers non-clickable", async () => {
    mockFarmApis();
    vi.mocked(attentionApi.list).mockResolvedValue([{
      type: "LOW_FEED_STOCK",
      severity: "WARNING",
      title: "Low feed stock",
      message: "Layer Mash has low stock.",
      item: "Layer Mash",
    }]);

    render(<App />);

    expect(await screen.findByText("Low feed stock")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Low feed stock/ })).not.toBeInTheDocument();
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
    fireEvent.click(within(screen.getByRole("navigation", { name: "Main navigation" })).getByRole("button", { name: "Production" }));
    await waitFor(() => expect(screen.getByRole("option", { name: "Select a layer batch" })).toBeInTheDocument());

    const eggBatch = screen.getByRole("option", { name: "Select a layer batch" }).parentElement;
    fireEvent.change(eggBatch, { target: { value: "layer-1" } });
    await waitFor(() => expect(screen.getByRole("heading", { name: "Record egg production" })).toBeInTheDocument());
    expect(within(eggBatch).getByRole("option", { name: "L-2026-001" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Weight & growth" }));
    await waitFor(() => expect(screen.getByRole("option", { name: "Select a broiler batch" })).toBeInTheDocument());
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

    fireEvent.click(await screen.findByRole("button", { name: "Flocks" }));
    await waitFor(() => expect(screen.getByRole("combobox", { name: "Batch dashboard" })).toBeInTheDocument());

    fireEvent.change(screen.getByRole("combobox", { name: "Batch dashboard" }), { target: { value: "broiler-1" } });

    await waitFor(() => expect(dashboardApi.batch).toHaveBeenLastCalledWith("broiler-1"));
    expect(screen.getByRole("option", { name: /B-2026-001 · BROILER/ })).toBeInTheDocument();
    expect(localStorage.getItem("grantino:lastBatchId")).toBe("broiler-1");
  });
  it("routes dashboard quick actions to validated forms with the last-used batch context", async () => {
    mockFarmApis({ currentBatches: batches });
    vi.mocked(feedApi.listTypes).mockResolvedValue([
      { id: "feed-layer", name: "Layer mash", unit: "bag", applicableType: "LAYER", status: "ACTIVE" },
      { id: "feed-broiler", name: "Broiler starter", unit: "bag", applicableType: "BROILER", status: "ACTIVE" },
    ]);
    render(<App />);
    await screen.findByRole("heading", { name: "Farm dashboard" });
    const dashboardNav = () => fireEvent.click(within(screen.getByRole("navigation", { name: "Main navigation" })).getByRole("button", { name: "Dashboard" }));

    fireEvent.click(await screen.findByRole("button", { name: "Record mortality" }));
    expect(window.location.hash).toContain("daily-work/mortality");
    expect(await screen.findByRole("combobox", { name: "Batch" })).toHaveValue("layer-1");

    dashboardNav();
    fireEvent.click(await screen.findByRole("button", { name: "Collect eggs" }));
    expect(window.location.hash).toContain("production/eggs");
    expect(await screen.findByRole("combobox", { name: "Layer batch" })).toHaveValue("layer-1");

    dashboardNav();
    fireEvent.click(await screen.findByRole("button", { name: "Record feed usage" }));
    expect(window.location.hash).toContain("feed-stock/feed");
    expect(await screen.findByRole("combobox", { name: "Feed type" })).toHaveValue("feed-layer");

    dashboardNav();
    fireEvent.click(await screen.findByRole("button", { name: "Record a sale" }));
    expect(window.location.hash).toContain("production/eggs");
    expect(await screen.findByRole("combobox", { name: "Layer batch" })).toHaveValue("layer-1");

    dashboardNav();
    fireEvent.click(await screen.findByRole("button", { name: "Record an expense" }));
    expect(window.location.hash).toContain("sales-money/expenses");
    expect(await screen.findByLabelText("Batch association")).toHaveValue("layer-1");
  });

  it("preserves unsaved farm form input while retrying after a backend outage", async () => {
    mockFarmApis();
    render(<App />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Farm dashboard" })).toBeInTheDocument());
    fireEvent.click(within(screen.getByRole("navigation", { name: "Main navigation" })).getByRole("button", { name: "Settings" }));

    const farmName = await screen.findByLabelText("Farm name");
    fireEvent.change(farmName, { target: { value: "Unsaved recovery test" } });
    fireEvent(window, new Event("pomas:backend-unavailable"));

    const retryButton = await screen.findByRole("button", { name: "Retry connection" });
    fireEvent.click(retryButton);

    await waitFor(() => expect(screen.queryByRole("button", { name: "Retry connection" })).not.toBeInTheDocument());
    expect(screen.getByLabelText("Farm name")).toHaveValue("Unsaved recovery test");
  });


});
