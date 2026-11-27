import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import ReviewCentrePage from "./ReviewCentrePage.jsx";
import { reportReviewApi } from "../services/api.js";

const fixtures = vi.hoisted(() => ({ detail: ({
  id: "submission-1", title: "Mortality report", reportType: "MORTALITY",
  authorId: "worker-1", authorFullName: "Worker One", lifecycleStatus: "SUBMITTED",
  status: "PENDING_REVIEW", currentVersion: 1, submittedVersion: 1,
  versions: [{ version: 1, payload: { batchId: "batch-1", quantity: 1 } }],
  checks: [], comments: [], decisions: [], assignments: [], history: [],
}) }));

vi.mock("../services/api.js", () => ({
  reportReviewApi: {
    queue: vi.fn().mockResolvedValue([fixtures.detail]),
    managers: vi.fn().mockResolvedValue([{ id: "manager-1", username: "manager", fullName: "Manager One" }]),
    get: vi.fn().mockResolvedValue(fixtures.detail),
    assign: vi.fn().mockResolvedValue({ ...fixtures.detail, assignedManagerId: "manager-1" }),
    comment: vi.fn().mockResolvedValue(fixtures.detail),
    decide: vi.fn().mockResolvedValue({ ...fixtures.detail, lifecycleStatus: "APPROVED", status: "APPROVED" }),
    correctWeight: vi.fn(),
    merge: vi.fn(),
  },
}));

describe("ReviewCentrePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    reportReviewApi.queue.mockResolvedValue([fixtures.detail]);
    reportReviewApi.managers.mockResolvedValue([{ id: "manager-1", username: "manager", fullName: "Manager One" }]);
    reportReviewApi.get.mockResolvedValue(fixtures.detail);
    reportReviewApi.merge.mockReset();
  });

  it("allows a manager to inspect and approve another user's exact submitted version", async () => {
    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.click(await screen.findByRole("button", { name: /Mortality report/ }));
    expect(await screen.findByText("Submitted report snapshot")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Approve version" }));
    await waitFor(() => expect(reportReviewApi.decide).toHaveBeenCalledWith("submission-1", "APPROVED", null));
    expect(await screen.findByText(/Official farm records remain unchanged/)).toBeInTheDocument();
  });

  it("reuses the same merge key after an interrupted host response", async () => {
    const approved = {
      ...fixtures.detail,
      lifecycleStatus: "APPROVED",
      status: "APPROVED",
      submittedVersion: 3,
      currentVersion: 3,
    };
    reportReviewApi.queue.mockResolvedValue([approved]);
    reportReviewApi.get.mockResolvedValue(approved);
    reportReviewApi.merge
      .mockRejectedValueOnce(new TypeError("Network connection was interrupted"))
      .mockResolvedValueOnce({ ...approved, lifecycleStatus: "MERGED", status: "MERGED" });

    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.click(await screen.findByRole("button", { name: /Mortality report/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Merge approved report" }));
    await waitFor(() => expect(reportReviewApi.merge).toHaveBeenCalledTimes(1));
    expect(await screen.findByText(/Network connection was interrupted/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Merge approved report" }));
    await waitFor(() => expect(reportReviewApi.merge).toHaveBeenCalledTimes(2));
    expect(reportReviewApi.merge.mock.calls[0][1]).toBe("merge-submission-1-3");
    expect(reportReviewApi.merge.mock.calls[1][1]).toBe("merge-submission-1-3");
  });

  it("prevents a manager from deciding on their own submission", async () => {
    const own = { ...fixtures.detail, authorId: "manager-1" };
    reportReviewApi.queue.mockResolvedValue([own]);
    reportReviewApi.get.mockResolvedValue(own);
    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.click(await screen.findByRole("button", { name: /Mortality report/ }));
    expect(await screen.findByText(/cannot approve or reject it/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve version" })).not.toBeInTheDocument();
  });
  it("reopens merged reports and records a typed weight correction", async () => {
    const merged = {
      ...fixtures.detail,
      title: "Weight report",
      reportType: "WEIGHT_RECORD",
      lifecycleStatus: "MERGED",
      status: "MERGED",
      versions: [{ version: 1, payload: {
        batchId: "broiler-batch-1", eventDate: "2026-11-20",
        sampleQuantity: 10, totalWeightKg: "20.000",
      } }],
      links: [{ id: "link-1", version: 1, recordType: "WEIGHT_RECORD", recordId: "weight-1" }],
      history: [],
    };
    const corrected = {
      ...merged,
      links: [
        ...merged.links,
        { id: "link-2", version: 1, recordType: "WEIGHT_RECORD", recordId: "weight-2" },
        { id: "link-3", version: 1, recordType: "WEIGHT_RECORD", recordId: "weight-3" },
      ],
      history: [
        { id: "event-1", version: 1, actorId: "manager-1", event: "POST_MERGE_CORRECTED", occurredAt: "2026-11-27T10:00:00Z", details: {
          originalRecordId: "weight-1", replacementRecordId: "weight-2", reason: "Incorrect sample count",
        } },
        { id: "event-2", version: 1, actorId: "manager-1", event: "POST_MERGE_CORRECTED", occurredAt: "2026-11-27T10:05:00Z", details: {
          originalRecordId: "weight-2", replacementRecordId: "weight-3", reason: "Corrected follow-up calibration",
        } },
      ],
    };
    reportReviewApi.queue.mockResolvedValue([merged]);
    reportReviewApi.get.mockResolvedValueOnce(merged).mockResolvedValueOnce(merged).mockResolvedValue(corrected);
    reportReviewApi.correctWeight.mockResolvedValue({ status: "CORRECTED" });

    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.change(await screen.findByLabelText("Status"), { target: { value: "MERGED" } });
    await waitFor(() => expect(reportReviewApi.queue).toHaveBeenCalledWith({ status: "MERGED", reportType: "" }));
    fireEvent.click(await screen.findByRole("button", { name: /Weight report/ }));
    expect(await screen.findByText("Correct latest effective weight record")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Correction reason"), { target: { value: "Incorrect sample count" } });
    fireEvent.change(screen.getByLabelText("Corrected birds sampled"), { target: { value: "12" } });
    fireEvent.change(screen.getByLabelText("Corrected total sample weight (kg)"), { target: { value: "24.000" } });
    fireEvent.click(screen.getByRole("button", { name: "Record correction" }));

    await waitFor(() => expect(reportReviewApi.correctWeight).toHaveBeenCalledWith("submission-1", {
      reason: "Incorrect sample count",
      replacement: {
        recordDate: "2026-11-20",
        sampleQuantity: 12,
        totalWeightKg: "24.000",
        notes: null,
      },
    }));
    expect(await screen.findByText(/original weight record was preserved and reversed/)).toBeInTheDocument();
    expect(await screen.findByText("Correct latest effective weight record")).toBeInTheDocument();
    expect(screen.getAllByText("weight-1").length).toBeGreaterThan(0);
    expect(screen.getAllByText("weight-2").length).toBeGreaterThan(0);
    expect(screen.getAllByText("weight-3").length).toBeGreaterThan(0);
    expect(screen.getByText("Incorrect sample count")).toBeInTheDocument();
    expect(screen.getByText("Corrected follow-up calibration")).toBeInTheDocument();
  });

  it("lets a Manager request changes with a reason and find the returned report", async () => {
    const returned = {
      ...fixtures.detail,
      lifecycleStatus: "CHANGES_REQUESTED",
      status: "CHANGES_REQUESTED",
    };
    reportReviewApi.decide.mockResolvedValue(returned);
    reportReviewApi.get.mockResolvedValueOnce(fixtures.detail).mockResolvedValue(returned);
    reportReviewApi.queue.mockResolvedValue([fixtures.detail]);

    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.click(await screen.findByRole("button", { name: /Mortality report/ }));
    fireEvent.change(await screen.findByLabelText(/Reason for returning or rejecting/i), {
      target: { value: "Please verify the batch count against the morning register." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Request changes" }));

    await waitFor(() => expect(reportReviewApi.decide).toHaveBeenCalledWith(
      "submission-1",
      "CHANGES_REQUESTED",
      "Please verify the batch count against the morning register.",
    ));
    expect(await screen.findByText("Report returned to its author for correction.")).toBeInTheDocument();
    expect(screen.getByText(/changes requested/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "CHANGES_REQUESTED" } });
    await waitFor(() => expect(reportReviewApi.queue).toHaveBeenCalledWith({
      status: "CHANGES_REQUESTED",
      reportType: "",
    }));
    expect(screen.getByRole("heading", { name: /Reports returned for changes/ })).toBeInTheDocument();
  });

  it("does not offer reviewer assignment after a report leaves the review queue", async () => {
    const approved = {
      ...fixtures.detail,
      lifecycleStatus: "APPROVED",
      status: "APPROVED",
      assignedManagerId: "manager-1",
    };
    reportReviewApi.queue.mockResolvedValue([approved]);
    reportReviewApi.get.mockResolvedValue(approved);

    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.click(await screen.findByRole("button", { name: /Mortality report/ }));

    expect(await screen.findByText(/Reviewer assignment is available only while a report is awaiting review/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Assign" })).toBeDisabled();
  });

});