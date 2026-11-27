import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import IncidentReportingPage from "./IncidentReportingPage.jsx";

const api = vi.hoisted(() => ({
  incidentApi: {
    list: vi.fn(), create: vi.fn(), acknowledge: vi.fn(), resolve: vi.fn(), escalate: vi.fn(),
  },
  reportReviewApi: { notifications: vi.fn(), markNotificationRead: vi.fn() },
  reportSubmissionApi: { references: vi.fn() },
  reportingConfigApi: {
    schedules: vi.fn(), createSchedule: vi.fn(), setScheduleEnabled: vi.fn(), missing: vi.fn(),
  },
}));

vi.mock("../services/api.js", () => api);

describe("IncidentReportingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.incidentApi.list.mockResolvedValue([]);
    api.incidentApi.create.mockResolvedValue({});
    api.incidentApi.acknowledge.mockResolvedValue({});
    api.incidentApi.resolve.mockResolvedValue({});
    api.incidentApi.escalate.mockResolvedValue({});
    api.reportReviewApi.notifications.mockResolvedValue([]);
    api.reportReviewApi.markNotificationRead.mockResolvedValue({});
    api.reportSubmissionApi.references.mockResolvedValue({ batches: [] });
    api.reportingConfigApi.schedules.mockResolvedValue([]);
    api.reportingConfigApi.createSchedule.mockResolvedValue({});
    api.reportingConfigApi.setScheduleEnabled.mockResolvedValue({});
    api.reportingConfigApi.missing.mockResolvedValue([]);
  });

  it("lets a Worker submit an emergency alert without normal report approval", async () => {
    render(<IncidentReportingPage currentUser={{ id: "worker-1", role: "WORKER" }} />);

    fireEvent.change(await screen.findByLabelText("Title"), { target: { value: "Water line stopped" } });
    fireEvent.change(screen.getByLabelText("What happened?"), {
      target: { value: "No water is reaching the drinkers in the active house." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send emergency alert" }));

    await waitFor(() => expect(api.incidentApi.create).toHaveBeenCalledWith(expect.objectContaining({
      title: "Water line stopped",
      severity: "MEDIUM",
      description: "No water is reaching the drinkers in the active house.",
    })));
    expect(await screen.findByText(/Managers have been notified/)).toBeTruthy();
    expect(api.reportingConfigApi.schedules).not.toHaveBeenCalled();
  });

  it("lets a Manager configure schedules and inspect missing reports", async () => {
    api.reportingConfigApi.missing.mockResolvedValue([{
      id: "schedule-1", reportType: "DAILY_OPERATIONS", scopeType: "FARM",
      dueAt: "2026-11-27T16:00:00+01:00", status: "MISSING",
    }]);
    render(<IncidentReportingPage currentUser={{ id: "manager-1", role: "MANAGER" }} />);

    fireEvent.click(await screen.findByRole("button", { name: /Schedules & missing reports/ }));
    fireEvent.change(screen.getByLabelText("Report type"), { target: { value: "DAILY_OPERATIONS" } });
    fireEvent.change(screen.getByLabelText("Due time (farm local)"), { target: { value: "17:30" } });
    fireEvent.click(screen.getByRole("button", { name: "Save schedule" }));

    await waitFor(() => expect(api.reportingConfigApi.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
      reportType: "DAILY_OPERATIONS",
      scopeType: "FARM",
      frequency: "DAILY",
      dueTime: "17:30",
      dayOfWeek: null,
      graceMinutes: 0,
    })));
    expect(await screen.findByText("MISSING")).toBeTruthy();
    expect(screen.getByText(/missing report is not a zero-activity report/i)).toBeTruthy();
  });

  it("loads notifications for the signed-in user and marks them read", async () => {
    api.reportReviewApi.notifications.mockResolvedValue([{
      id: "notice-1", type: "REPORT_APPROVED", entityType: "REPORT_SUBMISSION",
      entityId: "submission-1", title: "Your report was approved",
      message: "A Manager approved your report.", createdAt: "2026-11-27T10:00:00Z", readAt: null,
    }]);
    render(<IncidentReportingPage currentUser={{ id: "worker-1", role: "WORKER" }} />);

    fireEvent.click(await screen.findByRole("button", { name: /Notifications/ }));
    fireEvent.click(await screen.findByRole("button", { name: /Your report was approved/ }));

    await waitFor(() => expect(api.reportReviewApi.markNotificationRead).toHaveBeenCalledWith("notice-1"));
  });
});
