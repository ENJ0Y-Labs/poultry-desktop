import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import ReviewCentrePage from "./ReviewCentrePage.jsx";
import { reportReviewApi } from "../services/api.js";

const detail = vi.hoisted(() => ({
  id: "submission-1", title: "Mortality report", reportType: "MORTALITY",
  authorId: "worker-1", authorFullName: "Worker One", lifecycleStatus: "SUBMITTED",
  status: "PENDING_REVIEW", currentVersion: 1, submittedVersion: 1,
  versions: [{ version: 1, payload: { batchId: "batch-1", quantity: 1 } }],
  checks: [], comments: [], decisions: [], assignments: [], history: [],
}));

vi.mock("../services/api.js", () => ({
  reportReviewApi: {
    queue: vi.fn().mockResolvedValue([detail]),
    managers: vi.fn().mockResolvedValue([{ id: "manager-1", username: "manager", fullName: "Manager One" }]),
    get: vi.fn().mockResolvedValue(detail),
    assign: vi.fn().mockResolvedValue({ ...detail, assignedManagerId: "manager-1" }),
    comment: vi.fn().mockResolvedValue(detail),
    decide: vi.fn().mockResolvedValue({ ...detail, lifecycleStatus: "APPROVED", status: "APPROVED" }),
  },
}));

describe("ReviewCentrePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    reportReviewApi.queue.mockResolvedValue([detail]);
    reportReviewApi.managers.mockResolvedValue([{ id: "manager-1", username: "manager", fullName: "Manager One" }]);
    reportReviewApi.get.mockResolvedValue(detail);
  });

  it("allows a manager to inspect and approve another user's exact submitted version", async () => {
    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.click(await screen.findByRole("button", { name: /Mortality report/ }));
    expect(await screen.findByText("Submitted report snapshot")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Approve version" }));
    await waitFor(() => expect(reportReviewApi.decide).toHaveBeenCalledWith("submission-1", "APPROVED", null));
    expect(await screen.findByText(/Official farm records remain unchanged/)).toBeInTheDocument();
  });

  it("prevents a manager from deciding on their own submission", async () => {
    const own = { ...detail, authorId: "manager-1" };
    reportReviewApi.queue.mockResolvedValue([own]);
    reportReviewApi.get.mockResolvedValue(own);
    render(<ReviewCentrePage currentUser={{ id: "manager-1", role: "MANAGER" }} />);
    fireEvent.click(await screen.findByRole("button", { name: /Mortality report/ }));
    expect(await screen.findByText(/cannot approve or reject it/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve version" })).not.toBeInTheDocument();
  });
});
