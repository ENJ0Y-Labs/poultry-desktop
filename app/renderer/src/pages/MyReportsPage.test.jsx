import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import MyReportsPage from "./MyReportsPage.jsx";
import { reportAttachmentApi, reportSubmissionApi } from "../services/api.js";

const fixtures = vi.hoisted(() => ({
  draft: {
    id: "submission-1",
    title: "Morning mortality",
    reportType: "MORTALITY",
    lifecycleStatus: "DRAFT",
    status: "DRAFT",
    currentVersion: 1,
    submittedVersion: null,
    versions: [{
      version: 1,
      title: "Morning mortality",
      payload: {
        eventDate: "2026-11-26",
        batchId: "batch-1",
        quantity: 2,
        reason: "",
        notes: "",
      },
      createdAt: "2026-11-26T08:00:00Z",
    }],
    history: [],
    comments: [],
  },
}));

vi.mock("../services/api.js", () => ({
  reportAttachmentApi: {
    list: vi.fn().mockResolvedValue([]),
    upload: vi.fn(),
    download: vi.fn(),
  },
  reportSubmissionApi: {
    mine: vi.fn().mockResolvedValue([]),
    references: vi.fn().mockResolvedValue({
      batches: [{ id: "batch-1", code: "LAYER-01", type: "LAYER" }],
      feedTypes: [],
    }),
    create: vi.fn(),
    save: vi.fn(),
    submit: vi.fn(),
    get: vi.fn(),
    comment: vi.fn(),
  },
}));
vi.mock("../utils/moneyInput.js", () => ({
  parseNairaToKobo: value => Math.round(Number(value) * 100),
}));

function fillMortalityForm() {
  fireEvent.change(screen.getByLabelText("Report title"), { target: { value: "Morning mortality" } });
  fireEvent.change(screen.getByLabelText("Batch / flock"), { target: { value: "batch-1" } });
  fireEvent.change(screen.getByLabelText("Number of birds"), { target: { value: "2" } });
  fireEvent.change(screen.getByLabelText("Time of event"), { target: { value: "08:00" } });
}

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

  it("persists an offline draft locally without creating or submitting a server report", async () => {
    reportSubmissionApi.mine.mockRejectedValue(new Error("The local backend is not responding."));
    render(<MyReportsPage userId="worker-1" />);
    await screen.findByText(/No reports yet/);
    fillMortalityForm();
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));

    expect(await screen.findByText("Local draft")).toBeInTheDocument();
    const saved = JSON.parse(localStorage.getItem("pomas:report-submission-drafts:worker-1"));
    expect(saved).toHaveLength(1);
    expect(saved[0]).toMatchObject({
      reportType: "MORTALITY",
      title: "Morning mortality",
      fields: { batchId: "batch-1", quantity: "2" },
    });
    expect(reportSubmissionApi.create).not.toHaveBeenCalled();
    expect(reportSubmissionApi.submit).not.toHaveBeenCalled();
  });

  it("syncs Save draft to the backend but does not submit it", async () => {
    reportSubmissionApi.create.mockResolvedValue(fixtures.draft);
    reportSubmissionApi.mine.mockResolvedValue([{
      id: "submission-1",
      title: "Morning mortality",
      reportType: "MORTALITY",
      lifecycleStatus: "DRAFT",
      status: "DRAFT",
      currentVersion: 1,
      updatedAt: "2026-11-26T08:00:00Z",
    }]);

    render(<MyReportsPage userId="worker-1" />);
    await screen.findByRole("button", { name: "View" });
    fillMortalityForm();
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));

    await waitFor(() => expect(reportSubmissionApi.create).toHaveBeenCalledTimes(1));
    expect(reportSubmissionApi.create.mock.calls[0][0]).toMatchObject({
      reportType: "MORTALITY",
      title: "Morning mortality",
      payload: { eventDate: expect.any(String), batchId: "batch-1", quantity: 2 },
    });
    expect(reportSubmissionApi.create.mock.calls[0][1]).toMatch(/[a-z0-9-]{8,}/i);
    expect(reportSubmissionApi.submit).not.toHaveBeenCalled();
    expect(await screen.findByText(/Draft saved on this device and to the farm computer/)).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem("pomas:report-submission-drafts:worker-1"))[0].serverId).toBe("submission-1");
  });

  it("submits only after the worker explicitly chooses Submit for review", async () => {
    reportSubmissionApi.create.mockResolvedValue(fixtures.draft);
    reportSubmissionApi.submit.mockResolvedValue({
      ...fixtures.draft,
      status: "PENDING_REVIEW",
      lifecycleStatus: "SUBMITTED",
      submittedVersion: 1,
    });

    render(<MyReportsPage userId="worker-1" />);
    await screen.findByText(/No reports yet/);
    fillMortalityForm();
    fireEvent.click(screen.getByRole("button", { name: "Submit for review" }));

    await waitFor(() => expect(reportSubmissionApi.create).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(reportSubmissionApi.submit).toHaveBeenCalledWith("submission-1"));
    expect(await screen.findByText(/Report submitted/)).toBeInTheDocument();
    expect(localStorage.getItem("pomas:report-submission-drafts:worker-1")).toBe("[]");
  });

  it("keeps a local recovery copy and does not attempt server sync while offline", async () => {
    reportSubmissionApi.mine.mockRejectedValue(new Error("The local backend is not responding."));

    render(<MyReportsPage userId="worker-1" />);
    fillMortalityForm();
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));

    expect(await screen.findByText("Local draft")).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem("pomas:report-submission-drafts:worker-1"))).toHaveLength(1);
    expect(reportSubmissionApi.create).not.toHaveBeenCalled();
    expect(reportSubmissionApi.submit).not.toHaveBeenCalled();
    expect(await screen.findByText(/Server sync will be available when the farm computer reconnects/)).toBeInTheDocument();
  });


  it("restores a previously synced draft from its local recovery copy while offline", async () => {
    reportSubmissionApi.mine.mockRejectedValue(new Error("The local backend is not responding."));
    localStorage.setItem("pomas:report-submission-drafts:worker-1", JSON.stringify([{
      id: "local-copy-1",
      serverId: "submission-1",
      idempotencyKey: "retry-key-0001",
      reportType: "MORTALITY",
      title: "Recovered mortality report",
      fields: {
        eventDate: "2026-11-26", eventTime: "08:00", batchId: "batch-1",
        quantity: "4", reason: "Injury", notes: "",
      },
      updatedAt: "2026-11-26T08:00:00Z",
    }]));
    localStorage.setItem("pomas:report-submission-references:worker-1", JSON.stringify({
      batches: [{ id: "batch-1", code: "LAYER-01", type: "LAYER" }],
      feedTypes: [],
    }));

    render(<MyReportsPage userId="worker-1" />);
    expect(await screen.findByText("Offline recovery copy")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByLabelText("Report title")).toHaveValue("Recovered mortality report");
    expect(screen.getByLabelText("Number of birds")).toHaveValue(4);
    expect(screen.getByLabelText("Time of event")).toHaveValue("08:00");
  });

  it("shows immutable version snapshots and activity history for a server report", async () => {
    reportSubmissionApi.mine.mockResolvedValue([{
      id: "submission-1",
      title: "Morning mortality",
      reportType: "MORTALITY",
      lifecycleStatus: "SUBMITTED",
      status: "PENDING_REVIEW",
      currentVersion: 2,
      submittedVersion: 2,
    }]);
    reportSubmissionApi.get.mockResolvedValue({
      ...fixtures.draft,
      lifecycleStatus: "SUBMITTED",
      status: "PENDING_REVIEW",
      currentVersion: 2,
      submittedVersion: 2,
      versions: [
        ...fixtures.draft.versions,
        {
          version: 2,
          title: "Morning mortality",
          payload: { eventDate: "2026-11-26", batchId: "batch-1", quantity: 3, reason: "Injury", notes: "" },
          createdAt: "2026-11-26T09:00:00Z",
        },
      ],
      history: [{ id: "event-1", event: "SUBMITTED", occurredAt: "2026-11-26T09:01:00Z" }],
      checks: [{ id: "check-1", code: "REPORT_DATA_VALIDATION", outcome: "BLOCKED", explanation: "Choose an active batch." }],
    });

    render(<MyReportsPage userId="worker-1" />);
    fireEvent.click(await screen.findByRole("button", { name: "View" }));

    expect(await screen.findByText(/submitted version 2/i)).toBeInTheDocument();
    expect(screen.getByText(/Version history/)).toBeInTheDocument();
    expect(screen.getByText(/Activity history/)).toBeInTheDocument();
    expect(screen.getByText("Choose an active batch.")).toBeInTheDocument();
    expect(screen.getByText("SUBMITTED")).toBeInTheDocument();
    expect(screen.getAllByText(/Morning mortality/).length).toBeGreaterThan(1);
  });

  it("saves edits to an existing server draft as a new version without submitting", async () => {
    const original = {
      ...fixtures.draft,
      versions: [{
        ...fixtures.draft.versions[0],
        payload: { eventDate: "2026-11-26", batchId: "batch-1", quantity: 2, reason: "", notes: "" },
      }],
    };
    reportSubmissionApi.mine.mockResolvedValue([{
      id: "submission-1", title: "Morning mortality", reportType: "MORTALITY",
      lifecycleStatus: "DRAFT", status: "DRAFT", currentVersion: 1,
    }]);
    reportSubmissionApi.get.mockResolvedValue(original);
    reportSubmissionApi.save.mockResolvedValue({
      ...original,
      currentVersion: 2,
      versions: [...original.versions, {
        version: 2, title: "Morning mortality",
        payload: { eventDate: "2026-11-26", batchId: "batch-1", quantity: 2, reason: "Injury", notes: "" },
      }],
    });

    render(<MyReportsPage userId="worker-1" />);
    fireEvent.click(await screen.findByRole("button", { name: "View" }));
    fireEvent.change(await screen.findByLabelText("Suspected cause / reason"), { target: { value: "Injury" } });
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));

    await waitFor(() => expect(reportSubmissionApi.save).toHaveBeenCalledWith("submission-1", expect.objectContaining({
      reportType: "MORTALITY",
      payload: expect.objectContaining({ batchId: "batch-1", quantity: 2, reason: "Injury" }),
    })));
    expect(reportSubmissionApi.submit).not.toHaveBeenCalled();
  });
  it("uploads evidence to the current server draft version and shows the saved attachment", async () => {
    reportSubmissionApi.create.mockResolvedValue(fixtures.draft);
    reportSubmissionApi.get.mockResolvedValue(fixtures.draft);
    reportAttachmentApi.upload.mockResolvedValue({ id: "attachment-1" });
    reportAttachmentApi.list.mockResolvedValue([{
      id: "attachment-1",
      fileName: "mortality-photo.jpg",
      mimeType: "image/jpeg",
      fileSize: 2048,
      sha256: "a".repeat(64),
    }]);

    render(<MyReportsPage userId="worker-1" />);
    await screen.findByText(/No reports yet/);
    fillMortalityForm();
    fireEvent.change(screen.getByLabelText("Supporting evidence"), {
      target: { files: [new File(["evidence"], "mortality-photo.jpg", { type: "image/jpeg" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Upload evidence" }));

    await waitFor(() => expect(reportAttachmentApi.upload).toHaveBeenCalledWith(
      "submission-1",
      expect.objectContaining({ name: "mortality-photo.jpg", type: "image/jpeg" }),
    ));
    expect(await screen.findByText("mortality-photo.jpg")).toBeInTheDocument();
    expect(reportAttachmentApi.list).toHaveBeenCalledWith("submission-1");
  });


});
