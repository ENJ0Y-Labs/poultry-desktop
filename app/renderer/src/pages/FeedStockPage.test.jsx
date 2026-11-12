import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { feedApi } from "../services/api.js";

vi.mock("../services/api.js", () => ({
  feedApi: {
    openingBalances: vi.fn().mockResolvedValue([]),
    createOpeningBalance: vi.fn().mockResolvedValue({}),
    updateConversion: vi.fn().mockResolvedValue({}),
    updateType: vi.fn().mockResolvedValue({}),
  },
}));
// eslint-disable-next-line no-unused-vars
import FeedStockPage from "./FeedStockPage.jsx";
// eslint-disable-next-line no-unused-vars
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import { NavigationContext } from "../components/NavigationContext.jsx";

const contextValue = {
  // FeedStockPage always renders the purchase form, including when another tab is active.
  // Supply the draft context used by the real App provider so these tests exercise the page, not an incomplete fixture.
  draftStatuses: {},
  discardDraft: vi.fn(),
  markDraftReviewed: vi.fn(),
  formatMoney: value => String(value),
  farm: { currency: "NGN" },
  batches: [],
  feedTypes: [],
  setFeedTypes: vi.fn(),
  feedInventory: [],
  feedTypeForm: {
    name: "", brand: "", packagingName: "", packageWeightKg: "",
    unit: "bag", kgPerUnit: "", applicableTypes: ["LAYER", "BROILER", "TURKEY"],
  },
  setFeedTypeForm: vi.fn(),
  setError: vi.fn(),
  setSaving: vi.fn(),
  feedPurchaseForm: { feedTypeId: "", unit: "bag", purchaseDate: "", quantity: "", supplierId: "", totalCostMinor: "" },
  setFeedPurchaseForm: vi.fn(),
  inventoryItems: [],
  inventoryItemForm: { name: "", category: "SUPPLY", unit: "", reorderLevel: "" },
  setInventoryItemForm: vi.fn(),
  inventoryMovementForm: { itemId: "", date: "", movementType: "RECEIVE", quantity: "", reason: "", source: "", batchId: "" },
  setInventoryMovementForm: vi.fn(),
  inventoryMovements: [],
  suppliers: [],
  supplierForm: { name: "", phone: "", notes: "" },
  setSupplierForm: vi.fn(),
  saveSupplier: vi.fn(),
  archiveSupplier: vi.fn(),
  saving: false,
  saveInventoryItem: vi.fn(),
  loadInventoryMovements: vi.fn(),
  saveInventoryMovement: vi.fn(),
  createFeedType: vi.fn(),
  purchaseFeed: vi.fn(),
  feedQuantity: value => String(value),
};

describe("FeedStockPage", () => {
  beforeEach(() => vi.clearAllMocks());
  it("exposes opening stock entry without purchase or supplier fields", async () => {
    const context = {
      ...contextValue,
      feedTypes: [{ id: "feed-1", name: "Layer Mash", unit: "kg", status: "ACTIVE" }],
    };
    render(
      <NavigationContext.Provider value={{ activePage: "feed-stock", activeTab: "feed" }}>
        <FarmManagerContext.Provider value={context}>
          <FeedStockPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    expect(screen.getByRole("heading", { name: "Add opening stock" })).toBeInTheDocument();
    expect(screen.getByLabelText("Effective date")).toBeInTheDocument();
    expect(screen.getByLabelText("Reason / source")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Record opening stock" })).toBeDisabled();
    expect(screen.getByText(/does not create a supplier, purchase, invoice, or feed expense/i)).toBeInTheDocument();
    await waitFor(() => expect(feedApi.openingBalances).toHaveBeenCalledWith("feed-1"));
  });

  it("submits opening stock with the feed type unit and refreshes its history", async () => {
    feedApi.openingBalances.mockResolvedValue([]);
    const context = {
      ...contextValue,
      feedTypes: [{ id: "feed-1", name: "Layer Mash", unit: "kg", status: "ACTIVE" }],
    };
    render(
      <NavigationContext.Provider value={{ activePage: "feed-stock", activeTab: "feed" }}>
        <FarmManagerContext.Provider value={context}>
          <FeedStockPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    fireEvent.change(screen.getAllByLabelText("Feed type")[0], { target: { value: "feed-1" } });
    fireEvent.change(screen.getByLabelText("Effective date"), { target: { value: "2026-11-01" } });
    fireEvent.change(screen.getAllByLabelText("Quantity")[0], { target: { value: "25.5" } });
    fireEvent.change(screen.getByLabelText("Reason / source"), { target: { value: "Physical stock count" } });
    fireEvent.click(screen.getByRole("button", { name: "Record opening stock" }));

    await waitFor(() => expect(feedApi.createOpeningBalance).toHaveBeenCalledWith({
      feedTypeId: "feed-1",
      effectiveDate: "2026-11-01",
      quantity: 25.5,
      unit: "kg",
      reason: "Physical stock count",
    }));
    await waitFor(() => expect(feedApi.openingBalances).toHaveBeenCalledTimes(2));
  });

  it("edits brand and package identity without changing the feed type ID", async () => {
    const type = {
      id: "feed-a", name: "Grower Mash", brand: "Brand A", packagingName: "25 kg bag",
      packageWeightKg: 25, unit: "bag", status: "ACTIVE",
    };
    const updated = { ...type, brand: "Brand C", packagingName: "50 kg sack", packageWeightKg: null };
    feedApi.updateType.mockResolvedValue(updated);
    const context = { ...contextValue, feedTypes: [type] };
    render(
      <NavigationContext.Provider value={{ activePage: "feed-stock", activeTab: "feed" }}>
        <FarmManagerContext.Provider value={context}>
          <FeedStockPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    fireEvent.click(screen.getByRole("button", { name: "Edit details" }));
    const form = screen.getByRole("form", { name: "Edit feed product" });
    fireEvent.change(within(form).getByLabelText("Brand"), { target: { value: "Brand C" } });
    fireEvent.change(within(form).getByLabelText("Package name"), { target: { value: "50 kg sack" } });
    fireEvent.change(within(form).getByLabelText("Package weight in kg (leave blank if unknown)"), { target: { value: "" } });
    fireEvent.click(within(form).getByRole("button", { name: "Save product details" }));

    await waitFor(() => expect(feedApi.updateType).toHaveBeenCalledWith("feed-a", {
      name: "Grower Mash",
      brand: "Brand C",
      packagingName: "50 kg sack",
      packageWeightKg: null,
    }));
    expect(context.setFeedTypes).toHaveBeenCalledWith(expect.any(Function));
  });

  it("shows brands when selecting feed for a purchase", () => {
    const context = {
      ...contextValue,
      feedTypes: [
        { id: "feed-a", name: "Grower Mash", brand: "Brand A", unit: "bag", status: "ACTIVE" },
        { id: "feed-b", name: "Grower Mash", brand: "Brand B", unit: "bag", status: "ACTIVE" },
      ],
    };
    render(
      <NavigationContext.Provider value={{ activePage: "feed-stock", activeTab: "purchases" }}>
        <FarmManagerContext.Provider value={context}>
          <FeedStockPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    const select = screen.getByLabelText("Feed type");
    expect(within(select).getByRole("option", { name: "Grower Mash · Brand A (bag)" })).toBeInTheDocument();
    expect(within(select).getByRole("option", { name: "Grower Mash · Brand B (bag)" })).toBeInTheDocument();
  });

  it("renders package metadata fields without literal escaped newline text", () => {
    const { container } = render(
      <NavigationContext.Provider value={{ activePage: "feed-stock", activeTab: "feed" }}>
        <FarmManagerContext.Provider value={contextValue}>
          <FeedStockPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    expect(screen.getByLabelText("Package name")).toBeInTheDocument();
    expect(screen.getByLabelText("Package weight in kg (leave blank if unknown)")).toBeInTheDocument();
    expect(screen.getByLabelText("Unit e.g. bag or kg")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Layers" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Broilers" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Turkeys" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "Turkeys" }));
    expect(contextValue.setFeedTypeForm).toHaveBeenCalledWith(expect.objectContaining({
      applicableTypes: ["LAYER", "BROILER"],
    }));
    expect(container.textContent).not.toContain("\\n");
  });
});
