import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
// JSX component usage is not counted by the configured core no-unused-vars rule.
// eslint-disable-next-line no-unused-vars
import ProductionTrendChart from "./ProductionTrendChart.jsx";

vi.mock("recharts", () => ({
  CartesianGrid: () => null,
  Line: () => null,
  LineChart: ({ children }) => <div data-testid="line-chart">{children}</div>,
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

afterEach(() => cleanup());

describe("ProductionTrendChart", () => {
  it("shows loading, then an accessible chart using backend points", async () => {
    const loadTrend = vi.fn().mockResolvedValue({
      from: "2026-10-01",
      to: "2026-10-02",
      points: [
        { date: "2026-10-01", value: 12.5, recorded: true },
        { date: "2026-10-02", value: null, recorded: false },
      ],
    });
    render(<ProductionTrendChart batchId="batch-1" title="Layer HDP%" description="Layer trend" unit="percent" loadTrend={loadTrend} />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading layer hdp%");
    expect(await screen.findByRole("img", { name: /Layer HDP% line chart from 2026-10-01 to 2026-10-02/ })).toBeInTheDocument();
    expect(screen.getByTestId("line-chart")).toBeInTheDocument();
    expect(loadTrend).toHaveBeenCalledWith("batch-1", undefined, expect.any(String));
  });

  it("reloads the selected batch trend after a relevant successful write event", async () => {
    const loadTrend = vi.fn().mockResolvedValue({
      from: "2026-10-01",
      to: "2026-10-02",
      points: [{ date: "2026-10-01", value: 12.5, recorded: true }],
    });
    render(<ProductionTrendChart batchId="batch-1" title="Layer HDP%" description="Layer trend" unit="percent" loadTrend={loadTrend} />);
    await waitFor(() => expect(loadTrend).toHaveBeenCalledTimes(1));

    window.dispatchEvent(new CustomEvent("pomas:data-mutated", {
      detail: { path: "/batches/batch-1/eggs/collections", method: "POST" },
    }));

    await waitFor(() => expect(loadTrend).toHaveBeenCalledTimes(2));

    window.dispatchEvent(new CustomEvent("pomas:data-mutated", {
      detail: { path: "/batches/batch-1/mortality", method: "POST" },
    }));
    await waitFor(() => expect(loadTrend).toHaveBeenCalledTimes(3));
  });

  it("shows an explicit empty state when all days have missing records", async () => {
    const loadTrend = vi.fn().mockResolvedValue({
      from: "2026-10-01",
      to: "2026-10-02",
      points: [
        { date: "2026-10-01", value: null, recorded: false },
        { date: "2026-10-02", value: null, recorded: false },
      ],
    });
    render(<ProductionTrendChart batchId="batch-1" title="Average weight" description="Broiler trend" unit="kg" loadTrend={loadTrend} />);

    expect(await screen.findByText(/No production records in this period/)).toBeInTheDocument();
    expect(screen.queryByTestId("line-chart")).not.toBeInTheDocument();
  });

  it("shows backend failures without crashing the production page", async () => {
    const loadTrend = vi.fn().mockRejectedValue(new Error("Trend endpoint unavailable"));
    render(<ProductionTrendChart batchId="batch-1" title="Layer HDP%" description="Layer trend" unit="percent" loadTrend={loadTrend} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Trend endpoint unavailable");
  });
});
