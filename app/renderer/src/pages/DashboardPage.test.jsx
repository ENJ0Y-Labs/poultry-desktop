import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
vi.mock("../services/api.js", async importOriginal => {
  const actual = await importOriginal();
  return {
    ...actual,
    reportApi: {
      ...actual.reportApi,
      farm: vi.fn().mockResolvedValue({ sales: [], expenses: [], birdPopulation: [] }),
    },
  };
});
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import DashboardPage from "./DashboardPage.jsx";
// eslint-disable-next-line no-unused-vars -- JSX provider usage is not detected by the core rule
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX provider usage is not detected by the core rule
import { NavigationContext } from "../components/NavigationContext.jsx";

const auditRow = {
  id: "audit-1",
  userId: "owner-1",
  farmId: "farm-1",
  batchId: "batch-1",
  occurredAt: "2026-11-08T10:20:30.123Z",
  action: "UPDATE",
  entityType: "CUSTOMER",
  entityId: "customer-1",
  reason: "Corrected customer phone",
  changedFields: "[\"phone\"]",
  before: "{\"phone\":\"08000000000\"}",
  after: "{\"phone\":\"08012345678\"}",
};

const contextValue = {
  formatMoney: value => `NGN ${value ?? 0}`,
  formatQuantity: value => String(value ?? 0),
  formatCount: value => String(value ?? 0),
  crateSize: 30,
  farm: { currency: "NGN", timezone: "Africa/Lagos" },
  houses: [{ id: "house-1", name: "Pen One", code: "P1" }],
  batches: [{ id: "batch-1", code: "B-2026-001" }],
  feedTypes: [],
  feedInventory: [],
  dashboard: {
    asOf: "2026-11-08",
    totalBirds: 10,
    estimatedBirds: 0,
    estimatedBatches: 0,
    layerBirds: 10,
    broilerBirds: 0,
    turkeyBirds: 0,
    activeBatches: 1,
    totalHouses: 1,
    permanentHouses: 1,
    temporaryHouses: 0,
    occupiedHouses: 1,
    emptyHouses: 0,
    mortality: 0,
    eggsGood: 0,
    revenueMinor: 0,
    expensesMinor: 0,
    profitMinor: 0,
    inventoryAlerts: 0,
    feedSummary: { typeCount: 0, remainingQuantityMilli: 0, remainingCostMinor: null, consumedCostMinor: null },
  },
  attention: [],
  auditRows: [auditRow],
  applicationSettings: { dateFormat: "YYYY-MM-DD" },
  selectedBatchId: "batch-1",
  setMortalityForm: vi.fn(),
  setMortalityModalSignal: vi.fn(),
  setFeedUsageModalSignal: vi.fn(),
  setEggBatchId: vi.fn(),
  setEggCollectionForm: vi.fn(),
  setFeedUsageForm: vi.fn(),
  setDailyForm: vi.fn(),
  setBroilerBatchId: vi.fn(),
  setBirdSaleForm: vi.fn(),
  setEggSaleForm: vi.fn(),
  setExpenseForm: vi.fn(),
  setFeedPurchaseModalSignal: vi.fn(),
  loadEggsForBatch: vi.fn(),
  loadBroilerForBatch: vi.fn(),
};

describe("DashboardPage audit context drawer", () => {
  it("shows immutable audit identifiers and before/after snapshots", () => {
    render(
      <NavigationContext.Provider value={{ activePage: "dashboard", activeTab: "overview" }}>
        <FarmManagerContext.Provider value={contextValue}>
          <DashboardPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    const table = screen.getByRole("table", { name: "Recent audit activity" });
    expect(within(table).getByText("Corrected customer phone")).toBeInTheDocument();
    fireEvent.click(within(table).getByRole("button", { name: "View" }));

    const drawer = screen.getByRole("dialog", { name: "Updated" });
    expect(within(drawer).getByText("customer-1")).toBeInTheDocument();
    expect(within(drawer).getByText("owner-1")).toBeInTheDocument();
    expect(within(drawer).getByText("phone")).toBeInTheDocument();
    expect(within(drawer).getByRole("heading", { name: "Before" })).toBeInTheDocument();
    expect(within(drawer).getByText(/08000000000/)).toBeInTheDocument();
    expect(within(drawer).getByRole("heading", { name: "After" })).toBeInTheDocument();
    expect(within(drawer).getByText(/08012345678/)).toBeInTheDocument();
    expect(within(drawer).queryByText("Corrected customer phone")).not.toBeInTheDocument();
  });
});

describe("DashboardPage quick actions", () => {
  it("keeps duplicate task shortcuts out of the dashboard because they live in the global menu", () => {
    render(
      <NavigationContext.Provider value={{ activePage: "dashboard", activeTab: "overview" }}>
        <FarmManagerContext.Provider value={contextValue}>
          <DashboardPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    expect(screen.queryByRole("heading", { name: "Quick actions" })).not.toBeInTheDocument();
  });

  it("shows all farm metrics in one five-column grid in the requested reading order", () => {
    render(
      <NavigationContext.Provider value={{ activePage: "dashboard", activeTab: "overview" }}>
        <FarmManagerContext.Provider value={contextValue}>
          <DashboardPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    const kpis = screen.getByRole("region", { name: "Farm key performance indicators" });
    expect(kpis.querySelectorAll(".dashboard-kpi-grid .dashboard-kpi-card")).toHaveLength(18);
    expect(kpis.querySelectorAll(".dashboard-kpi-card-primary")).toHaveLength(3);
    expect(within(kpis).queryByText("FLOCK & FACILITY BREAKDOWN")).not.toBeInTheDocument();
    expect(Array.from(kpis.querySelectorAll(".dashboard-kpi-card-label"), node => node.textContent)).toEqual([
      "Total birds", "Estimated birds", "Estimated batches", "Layer birds", "Broiler birds",
      "Turkey birds", "Active batches", "Total houses", "Permanent houses", "Temporary houses",
      "Occupied houses", "Empty houses", "Mortality", "Good eggs", "Revenue today",
      "Expenses today", "Profit today", "Inventory alerts",
    ]);
  });

  it("keeps date-range reporting controls off the dashboard and displays today's activity", () => {
    const dailyContext = {
      ...contextValue,
      dashboard: {
        ...contextValue.dashboard,
        revenueMinor: 900,
        expensesMinor: 400,
        period: { revenueMinor: 120, expensesMinor: 20, mortality: 1, eggsGood: 30 },
      },
    };

    render(
      <NavigationContext.Provider value={{ activePage: "dashboard", activeTab: "overview" }}>
        <FarmManagerContext.Provider value={dailyContext}>
          <DashboardPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    expect(screen.getByText("Today’s farm overview")).toBeInTheDocument();
    expect(screen.queryByText("Reporting period")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Reporting period")).not.toBeInTheDocument();
    expect(screen.getByText("Revenue today").parentElement).toHaveTextContent("NGN 120");
    expect(screen.getByText("Expenses today").parentElement).toHaveTextContent("NGN 20");
    expect(screen.getByText("Mortality").parentElement).toHaveTextContent("1");
  });
});
