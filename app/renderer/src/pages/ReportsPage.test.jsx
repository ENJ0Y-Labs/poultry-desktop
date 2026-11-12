import { useState } from "react";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ReportsPage from "./ReportsPage.jsx";
// eslint-disable-next-line no-unused-vars -- JSX provider usage is not detected by the core rule
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX provider usage is not detected by the core rule
import { NavigationContext } from "../components/NavigationContext.jsx";
import { reportApi } from "../services/api.js";

vi.mock("../services/api.js", () => ({
  reportApi: {
    farmCsv: vi.fn().mockResolvedValue("farm csv"),
    farmPdf: vi.fn().mockResolvedValue(new Blob(["pdf"])),
    batchCsv: vi.fn().mockResolvedValue("batch csv"),
    batchPdf: vi.fn().mockResolvedValue(new Blob(["pdf"])),
  },
  productionTrendApi: {
    layerHdp: vi.fn(),
    broilerAverageWeight: vi.fn(),
  },
}));

vi.mock("../utils/farmUx.js", () => ({
  rememberLastBatchId: vi.fn(),
}));

vi.mock("../components/ProductionTrendChart.jsx", () => ({
  default: () => null,
}));

const context = {
  formatMoney: value => String(value ?? "—"),
  formatQuantity: value => String(value ?? "—"),
  formatPercent: value => String(value ?? "—"),
  formatKg: value => String(value ?? "—"),
  formatMetric: value => String(value ?? "—"),
  formatCount: value => String(value ?? "—"),
  feedQuantity: value => String(value ?? "—"),
  farm: { currency: "NGN" },
  houses: [
    { id: "house-1", name: "House One", code: "H1" },
    { id: "house-2", name: "House Two", code: "H2" },
  ],
  batches: [
    { id: "batch-1", code: "LAYER-01", type: "LAYER", houseId: "house-1" },
    { id: "batch-2", code: "BROILER-02", type: "BROILER", houseId: "house-2" },
  ],
  farmReport: null,
  batchReport: null,
  reportExporting: false,
  dateRange: { preset: "CUSTOM", from: "2026-10-03", to: "2026-10-03" },
  setDateRange: vi.fn(),
  loadReport: vi.fn(),
  loadBatchReport: vi.fn(),
  exportCsv: vi.fn(),
  exportPdf: vi.fn(),
};

function renderReports(activeTab, reportExporting = false) {
  // eslint-disable-next-line no-unused-vars -- rendered as a JSX component
  function Harness() {
    const [selectedBatchId, setSelectedBatchId] = useState("batch-1");
    const value = { ...context, reportExporting, selectedBatchId, setSelectedBatchId };
    return (
      <NavigationContext.Provider value={{ activePage: "reports", activeTab }}>
        <FarmManagerContext.Provider value={value}>
          <ReportsPage />
        </FarmManagerContext.Provider>
      </NavigationContext.Provider>
    );
  }
  return render(<Harness />);
}

describe("ReportsPage filters", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("narrows the batch selector by house and prevents exporting a batch outside that house", async () => {
    const { container } = renderReports("batch-report");
    const batchSection = container.querySelector("#batch-reports");
    const houseSelect = within(batchSection).getByRole("combobox", { name: "Filter batches by house" });
    const batchSelect = within(batchSection).getByRole("combobox", { name: "Batch report" });
    const exportButton = within(batchSection).getByRole("button", { name: "Export CSV" });

    expect(within(batchSelect).getByRole("option", { name: /LAYER-01/ })).toBeInTheDocument();

    fireEvent.change(houseSelect, { target: { value: "house-2" } });

    expect(batchSelect).toHaveValue("");
    expect(within(batchSelect).queryByRole("option", { name: /LAYER-01/ })).not.toBeInTheDocument();
    expect(exportButton).toBeDisabled();

    fireEvent.change(batchSelect, { target: { value: "batch-2" } });

    expect(context.loadBatchReport).toHaveBeenCalledWith("batch-2", "2026-10-03", "2026-10-03");
    expect(exportButton).toBeEnabled();

    fireEvent.click(exportButton);
    expect(context.exportCsv).toHaveBeenCalledTimes(1);
    const [loadExport, filename] = context.exportCsv.mock.calls[0];
    expect(filename).toBe("batch-report.csv");
    await loadExport();
    expect(reportApi.batchCsv).toHaveBeenCalledWith("batch-2", null, "2026-10-03", "2026-10-03", null);
  });

  it("shows export progress and prevents duplicate downloads", () => {
    const { container } = renderReports("farm-report", true);
    const farmSection = container.querySelector("#reports");

    expect(screen.getByText("Preparing report export. Larger reports may take a little longer.")).toBeInTheDocument();
    const exportButtons = within(farmSection).getAllByRole("button", { name: "Preparing…" });
    expect(exportButtons).toHaveLength(2);
    exportButtons.forEach(button => expect(button).toBeDisabled());
  });

  it("updates the inclusive reporting range when a boundary date changes", () => {
    renderReports("farm-report");

    fireEvent.change(screen.getByLabelText("Range start date"), {
      target: { value: "2026-10-02" },
    });

    expect(context.setDateRange).toHaveBeenCalledWith({
      preset: "CUSTOM", from: "2026-10-02", to: "2026-10-03",
    });
  });
});
