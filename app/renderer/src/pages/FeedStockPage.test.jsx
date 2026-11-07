import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import FeedStockPage from "./FeedStockPage.jsx";
// eslint-disable-next-line no-unused-vars
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import { NavigationContext } from "../components/NavigationContext.jsx";

const contextValue = {
  formatMoney: value => String(value),
  farm: { currency: "NGN" },
  batches: [],
  feedTypes: [],
  setFeedTypes: vi.fn(),
  feedInventory: [],
  feedTypeForm: {
    name: "", brand: "", packagingName: "", packageWeightKg: "",
    unit: "bag", kgPerUnit: "", applicableType: "BOTH",
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
    expect(container.textContent).not.toContain("\\n");
  });
});
