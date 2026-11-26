import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import MyReportsPage from "./MyReportsPage.jsx";
import { reportSubmissionApi } from "../services/api.js";

vi.mock("../services/api.js", () => ({
  reportSubmissionApi: {
    mine: vi.fn().mockResolvedValue([]),
    references: vi.fn().mockResolvedValue({
      batches: [{ id: "batch-1", code: "LAYER-01", type: "LAYER" }],
      feedTypes: [],
    }),
    create: vi.fn(), save: vi.fn(),
    submit: vi.fn(), get: vi.fn(), comment: vi.fn(),
  },
}));
vi.mock("../utils/moneyInput.js", () => ({
  parseNairaToKobo: value => Math.round(Number(value) * 100),
}));

describe("MyReportsPage", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    reportSubmissionApi.mine.mockResolvedValue([]);
    reportSubmissionApi.references.mockResolvedValue({
      batches: [{ id: "batch-1", code: "LAYER-01", type: "LAYER" }],
      feedTypes: [],
    });
  });

  it("saves drafts locally without submitting them automatically", async () => {
    render(<MyReportsPage userId="worker-1" />);
    await screen.findByText(/No reports yet/);
    fireEvent.change(screen.getByLabelText("Report title"), { target: { value: "Morning mortality" } });
    fireEvent.change(screen.getByLabelText("Batch / flock"), { target: { value: "batch-1" } });
    fireEvent.change(screen.getByLabelText("Number of birds"), { target: { value: "2" } });
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("Local draft")).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem("pomas:report-submission-drafts:worker-1"))).toHaveLength(1);
    expect(reportSubmissionApi.create).not.toHaveBeenCalled();
    expect(reportSubmissionApi.submit).not.toHaveBeenCalled();
  });

  it("creates an idempotent server submission only after explicit submit", async () => {
    reportSubmissionApi.create.mockResolvedValue({
      id: "submission-1", lifecycleStatus: "DRAFT", status: "DRAFT",
      currentVersion: 1, title: "Morning mortality", reportType: "MORTALITY",
      versions: [{ version: 1, payload: { eventDate: "2026-11-26", batchCode: "LAYER-01", quantity: 2 } }],
    });
    reportSubmissionApi.submit.mockResolvedValue({
      id: "submission-1", title: "Morning mortality", reportType: "MORTALITY",
      status: "PENDING_REVIEW", lifecycleStatus: "SUBMITTED", submittedVersion: 1,
      versions: [], history: [], comments: [],
    });
    render(<MyReportsPage userId="worker-1" />);
    await screen.findByText(/No reports yet/);
    fireEvent.change(screen.getByLabelText("Report title"), { target: { value: "Morning mortality" } });
    fireEvent.change(screen.getByLabelText("Batch code"), { target: { value: "LAYER-01" } });
    fireEvent.change(screen.getByLabelText("Number of birds"), { target: { value: "2" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit for review" }));
    await waitFor(() => expect(reportSubmissionApi.create).toHaveBeenCalledTimes(1));
    expect(reportSubmissionApi.create.mock.calls[0][0]).toMatchObject({
      reportType: "MORTALITY", title: "Morning mortality",
      payload: { batchId: "batch-1", quantity: 2 },
    });
    expect(reportSubmissionApi.create.mock.calls[0][1]).toMatch(/[a-z0-9-]{8,}/i);
    await waitFor(() => expect(reportSubmissionApi.submit).toHaveBeenCalledWith("submission-1"));
    expect(await screen.findByText(/Report submitted/)).toBeInTheDocument();
  });
});
