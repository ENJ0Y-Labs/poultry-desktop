import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import FlocksPage from "./FlocksPage.jsx";
// eslint-disable-next-line no-unused-vars -- JSX provider usage is not detected by the core rule
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX provider usage is not detected by the core rule
import { NavigationContext } from "../components/NavigationContext.jsx";

const house = {
  id: "house-1",
  name: "North Pen",
  code: "PEN-01",
  houseType: "PERMANENT",
  status: "ACTIVE",
  notes: "Recently repaired roof",
};

const batch = {
  id: "batch-1",
  code: "B-2026-001",
  type: "BROILER",
  status: "ACTIVE",
  placementDate: "2026-10-12",
  initialBirdCount: 250,
  populationCountStatus: "VERIFIED",
  houseId: "house-1",
  supplierId: "supplier-1",
};

const contextValue = {
  formatMoney: value => `NGN ${value ?? 0}`,
  formatQuantity: value => String(value ?? 0),
  formatPercent: value => String(value ?? 0),
  formatKg: value => String(value ?? 0),
  formatMetric: value => String(value ?? 0),
  farm: { currency: "NGN" },
  houses: [house],
  batches: [batch],
  suppliers: [{ id: "supplier-1", name: "Daystar Chicks" }],
  batchForm: { type: "LAYER", placementDate: "2026-11-01", houseId: "house-1", initialBirdCount: "", populationCountStatus: "ESTIMATED", purchaseCostRecorded: false, supplierId: "", purchaseCostMinor: "" },
  setBatchForm: vi.fn(),
  costForm: { batchId: "", eventDate: "", amountMinor: "", reason: "" },
  setCostForm: vi.fn(),
  houseForm: { name: "", code: "", notes: "", houseType: "PERMANENT" },
  setHouseForm: vi.fn(),
  saving: false,
  selectedBatchId: "batch-1",
  setSelectedBatchId: vi.fn(),
  batchDashboard: {
    batch: { id: "batch-1", type: "BROILER", status: "ACTIVE" },
    population: { currentBirds: 230, mortality: 15, culling: 5, sold: 0 },
    feed: { totalQuantityMilli: 45000, totalCostMinor: 125000 },
    expensesMinor: 20000,
    revenueMinor: 0,
    profitMinor: -145000,
  },
  createBatch: vi.fn(),
  addBirdCost: vi.fn(),
  markBatchSold: vi.fn(),
  reopenBatch: vi.fn(),
  addHouse: vi.fn(),
  editHouse: vi.fn(),
  editingHouseId: "",
  cancelHouseEdit: vi.fn(),
  populationAdjustmentForm: { effectiveDate: "", newCount: "", reason: "" },
  setPopulationAdjustmentForm: vi.fn(),
  populationAdjustments: [],
  savePopulationAdjustment: vi.fn(),
  markPopulationEstimated: vi.fn(),
  editingBatchId: "",
  batchSetupForm: { houseId: "house-1", supplierId: "supplier-1" },
  setBatchSetupForm: vi.fn(),
  saveBatchSetup: vi.fn(),
  editBatchSetup: vi.fn(),
  cancelBatchSetup: vi.fn(),
};

function renderFlocks(activeTab) {
  return render(
    <NavigationContext.Provider value={{ activePage: "flocks", activeTab }}>
      <FarmManagerContext.Provider value={contextValue}>
        <FlocksPage />
      </FarmManagerContext.Provider>
    </NavigationContext.Provider>
  );
}

describe("FlocksPage contextual details", () => {
  it("shows assigned batch history and notes in the house drawer", () => {
    renderFlocks("houses");

    const table = screen.getByRole("table", { name: "Houses and pens" });
    expect(within(table).queryByText("Recently repaired roof")).not.toBeInTheDocument();
    fireEvent.click(within(table).getByRole("button", { name: "View" }));

    const drawer = screen.getByRole("dialog", { name: "North Pen" });
    expect(within(drawer).getByText("Recently repaired roof")).toBeInTheDocument();
    expect(within(drawer).getByRole("heading", { name: "Batch history" })).toBeInTheDocument();
    expect(within(drawer).getByText("B-2026-001")).toBeInTheDocument();
    expect(within(drawer).getByRole("button", { name: "Edit house" })).toBeInTheDocument();

    fireEvent.click(within(drawer).getByRole("button", { name: "Edit house" }));
    expect(contextValue.editHouse).toHaveBeenCalledWith(house);
    expect(screen.queryByRole("dialog", { name: "North Pen" })).not.toBeInTheDocument();
  });

  it("shows source and available operational figures in the batch drawer", () => {
    renderFlocks("flocks");

    const table = screen.getByRole("table", { name: "Batch records" });
    fireEvent.click(within(table).getByRole("button", { name: "View" }));

    const drawer = screen.getByRole("dialog", { name: "B-2026-001" });
    expect(within(drawer).getByText("Daystar Chicks")).toBeInTheDocument();
    expect(within(drawer).getByText("230")).toBeInTheDocument();
    expect(within(drawer).getByText("NGN 125000")).toBeInTheDocument();
    expect(within(drawer).getByRole("button", { name: "Edit batch setup" })).toBeInTheDocument();
  });

  it("completes batch registration through the review step", () => {
    const context = {
      ...contextValue,
      batchForm: { ...contextValue.batchForm, initialBirdCount: "250", purchaseCostRecorded: false },
    };
    render(
      <NavigationContext.Provider value={{ activePage: "flocks", activeTab: "flocks" }}>
        <FarmManagerContext.Provider value={context}>
          <FlocksPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    fireEvent.click(screen.getByRole("button", { name: "Register batch" }));
    expect(screen.getByText("STEP 1 OF 5")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByText("STEP 2 OF 5")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByText("STEP 3 OF 5")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByText("STEP 4 OF 5")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByText("STEP 5 OF 5")).toBeInTheDocument();
    const dialog = screen.getByRole("dialog", { name: "Register batch" });
    expect(within(dialog).getByText("250", { exact: true })).toBeInTheDocument();
    expect(within(dialog).getByText("North Pen", { exact: true })).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole("button", { name: "Create batch" }));
    expect(context.createBatch).toHaveBeenCalledTimes(1);
  });

});