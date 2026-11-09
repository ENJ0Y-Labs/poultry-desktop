import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import DailyWorkPage from "./DailyWorkPage.jsx";
// eslint-disable-next-line no-unused-vars
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import { NavigationContext } from "../components/NavigationContext.jsx";

describe("DailyWorkPage feed applicability", () => {
  it("shows only active feed types configured for the selected batch bird type", () => {
    const contextValue = {
      batches: [
        { id: "layer-batch", code: "L-001", type: "LAYER", status: "ACTIVE" },
        { id: "broiler-batch", code: "B-001", type: "BROILER", status: "ACTIVE" },
      ],
      dailyForm: { batchId: "layer-batch", date: "2026-11-07", notes: "", water: "" },
      setDailyForm: vi.fn(),
      dailyRecord: null,
      dailyRecordLoading: false,
      waterUnitLabel: "litres",
      mortalityForm: { batchId: "layer-batch", date: "2026-11-07", quantity: "", reason: "", notes: "" },
      setMortalityForm: vi.fn(),
      cullingForm: { batchId: "layer-batch", date: "2026-11-07", quantity: "", reason: "", notes: "" },
      setCullingForm: vi.fn(),
      saving: false,
      saveDailyRecord: vi.fn(),
      recordPopulationEvent: vi.fn(),
      feedTypes: [
        { id: "layer-feed", name: "Layer Mash", unit: "bag", status: "ACTIVE", applicableTypes: ["LAYER"] },
        { id: "broiler-feed", name: "Broiler Starter", unit: "bag", status: "ACTIVE", applicableTypes: ["BROILER"] },
        { id: "turkey-feed", name: "Turkey Grower", unit: "bag", status: "ACTIVE", applicableTypes: ["TURKEY"] },
        { id: "archived-layer", name: "Archived Layer Feed", unit: "bag", status: "ARCHIVED", applicableTypes: ["LAYER"] },
        { id: "shared-feed", name: "Shared Feed", unit: "kg", status: "ACTIVE", applicableTypes: ["LAYER", "BROILER"] },
      ],
      feedUsageForm: { batchId: "layer-batch", feedTypeId: "", usageDate: "2026-11-07", quantity: "", unit: "bag", reason: "" },
      setFeedUsageForm: vi.fn(),
      useFeed: vi.fn(),
      feedQuantity: value => String(value),
    };

    render(
      <NavigationContext.Provider value={{ activePage: "daily-work", activeTab: "daily-record" }}>
        <FarmManagerContext.Provider value={contextValue}>
          <DailyWorkPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );

    fireEvent.click(screen.getByRole("button", { name: "Record feed usage" }));
    const selectors = screen.getAllByRole("combobox", { name: "Feed type" });
    expect(selectors).toHaveLength(1);
    expect(within(selectors[0]).getByRole("option", { name: "Layer Mash (bag)" })).toBeInTheDocument();
    expect(within(selectors[0]).getByRole("option", { name: "Shared Feed (kg)" })).toBeInTheDocument();
    expect(within(selectors[0]).queryByRole("option", { name: "Broiler Starter (bag)" })).not.toBeInTheDocument();
    expect(within(selectors[0]).queryByRole("option", { name: "Turkey Grower (bag)" })).not.toBeInTheDocument();
    expect(within(selectors[0]).queryByRole("option", { name: "Archived Layer Feed (bag)" })).not.toBeInTheDocument();
  });
});
