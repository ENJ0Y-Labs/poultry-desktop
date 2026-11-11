import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { reportApi } from "../services/api.js";
// eslint-disable-next-line no-unused-vars -- BatchComparison is rendered as JSX.
import { BatchComparison } from "./ReportsPage.jsx";

vi.mock("recharts", () => ({
  BarChart: ({ children }) => <div>{children}</div>,
  Bar: () => null,
  CartesianGrid: () => null,
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

vi.mock("../services/api.js", () => ({
  reportApi: { compare: vi.fn() },
  productionTrendApi: { layerHdp: vi.fn(), broilerAverageWeight: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const batches = [
  { id: "layer-1", code: "L-001", type: "LAYER", placementDate: "2026-01-01", houseId: "house-1" },
  { id: "layer-2", code: "L-002", type: "LAYER", placementDate: "2026-03-01", houseId: "house-1" },
  { id: "broiler-1", code: "B-001", type: "BROILER", placementDate: "2026-04-01", houseId: "house-2" },
];

const commonProps = {
  batches,
  houses: [
    { id: "house-1", name: "Layer House", code: "H1" },
    { id: "house-2", name: "Broiler House", code: "H2" },
  ],
  dateRange: { from: "2026-10-01", to: "2026-10-31", preset: "custom" },
  setDateRange: vi.fn(),
  farm: { currency: "NGN", timezone: "Africa/Lagos" },
  applicationSettings: { dateFormat: "DD/MM/YYYY" },
  formatMoney: (value, currency) => currency + " " + value,
  formatKg: value => value + " kg",
  formatMetric: value => String(value),
  formatCount: value => String(value),
  formatPercent: value => value + "%",
  feedQuantity: value => String(value),
};

function report(batch, comparisonMetrics = {}) {
  return { batch, comparisonMetrics };
}

describe("BatchComparison", () => {
  it("shows layer-only egg metrics and excludes meat-bird metrics", async () => {
    reportApi.compare.mockResolvedValue({
      reports: [
        report(batches[0], { eggProduction: 1200, currentBirds: 900 }),
        report(batches[1], { eggProduction: 1500, currentBirds: 850 }),
      ],
    });
    render(<BatchComparison {...commonProps} />);

    fireEvent.click(screen.getByLabelText(/L-001/));
    fireEvent.click(screen.getByLabelText(/L-002/));

    await waitFor(() => expect(reportApi.compare).toHaveBeenCalledWith(
      ["layer-1", "layer-2"], "2026-10-01", "2026-10-31"
    ));
    expect(await screen.findByText("Eggs collected")).toBeInTheDocument();
    expect(screen.queryByText("Average weight at period end")).not.toBeInTheDocument();
    expect(screen.queryByText("FCR (batch-to-date)")).not.toBeInTheDocument();
  });

  it("hides egg and meat-bird-only metrics for a mixed selection", async () => {
    reportApi.compare.mockResolvedValue({
      reports: [
        report(batches[0], { eggProduction: 1200 }),
        report(batches[2], { averageWeightKg: 1.2, fcr: 1.8 }),
      ],
    });
    render(<BatchComparison {...commonProps} />);

    fireEvent.click(screen.getByLabelText(/L-001/));
    fireEvent.click(screen.getByLabelText(/B-001/));

    await waitFor(() => expect(reportApi.compare).toHaveBeenCalled());
    expect(await screen.findByText("Comparison table")).toBeInTheDocument();
    expect(screen.queryByText("Eggs collected")).not.toBeInTheDocument();
    expect(screen.queryByText("Average weight at period end")).not.toBeInTheDocument();
    expect(screen.queryByText("FCR (batch-to-date)")).not.toBeInTheDocument();
  });

  it("applies bird-type filters to both the selected comparison and visible table", async () => {
    reportApi.compare.mockResolvedValue({
      reports: [
        report(batches[0], { currentBirds: 900 }),
        report(batches[1], { currentBirds: 850 }),
      ],
    });
    render(<BatchComparison {...commonProps} />);

    fireEvent.click(screen.getByLabelText(/L-001/));
    fireEvent.click(screen.getByLabelText(/L-002/));
    await screen.findByText("Comparison table");

    fireEvent.change(screen.getByLabelText("Filter batches by bird type"), {
      target: { value: "BROILER" },
    });

    await waitFor(() => expect(screen.getByText(/Select at least two batches/)).toBeInTheDocument());
    expect(screen.queryByText("Comparison table")).not.toBeInTheDocument();
  });

  it("removes a selected batch and reapplies the comparison filter", async () => {
    reportApi.compare.mockResolvedValue({
      reports: [
        report(batches[0], { currentBirds: 900 }),
        report(batches[1], { currentBirds: 850 }),
      ],
    });
    render(<BatchComparison {...commonProps} />);

    fireEvent.click(screen.getByLabelText(/L-001/));
    fireEvent.click(screen.getByLabelText(/L-002/));
    await screen.findByText("Comparison table");

    fireEvent.click(screen.getByRole("button", { name: "Remove L-001" }));
    await waitFor(() => expect(screen.getByText(/Select at least two batches/)).toBeInTheDocument());
  });
});
