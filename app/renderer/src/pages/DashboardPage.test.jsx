import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
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
