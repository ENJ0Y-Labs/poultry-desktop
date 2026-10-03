import { describe, expect, it, vi, beforeEach } from "vitest";
import { authApi, attentionApi, reportApi, settingsApi, expenseCategoryApi, accountApi } from "./api.js";

describe("API client", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("includes credentials so session cookies are sent", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ ok: true, data: { id: "user-1" } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await authApi.me();

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/auth/me"),
      expect.objectContaining({
        credentials: "include",
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
      })
    );
  });

  it("loads backend-derived attention rows with credentials", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({
        ok: true,
        data: [{
          type: "LOW_FEED_STOCK",
          severity: "WARNING",
          title: "Low feed stock",
          message: "Layer Mash has about 2.0 days of stock."
        }]
      }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const rows = await attentionApi.list("2026-10-03");

    expect(rows[0].type).toBe("LOW_FEED_STOCK");
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/attention?asOf=2026-10-03"),
      expect.objectContaining({ credentials: "include" })
    );
  });

  it("loads settings and account endpoints", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ ok: true, data: { enabled: true, directory: "D:/backups", intervalMs: 86400000 } }), { status: 200 })
    );
    await settingsApi.backup();
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/settings/backup"), expect.objectContaining({ credentials: "include" }));

    fetchMock.mockResolvedValue(new Response(JSON.stringify({ ok: true, data: [{ id: "cat-1", name: "FEED", status: "ACTIVE" }] }), { status: 200 }));
    const categories = await expenseCategoryApi.list();
    expect(categories[0].name).toBe("FEED");

    fetchMock.mockResolvedValue(new Response(JSON.stringify({ ok: true, data: { id: "u1", email: "owner@example.com", fullName: "Owner" } }), { status: 200 }));
    const account = await accountApi.get();
    expect(account.email).toBe("owner@example.com");
  });

  it("downloads a report PDF as a blob", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("%PDF-test", {
        status: 200,
        headers: { "Content-Type": "application/pdf" },
      })
    );

    const blob = await reportApi.farmPdf("2026-10-03");

    expect(blob).toBeInstanceOf(Blob);
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/reports/farm.pdf?asOf=2026-10-03"),
      expect.objectContaining({ credentials: "include" })
    );
  });

  it("preserves structured backend errors", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({
        ok: false,
        error: {
          code: "INSUFFICIENT_BIRDS",
          message: "The sale exceeds the available birds.",
          details: { available: 3 }
        }
      }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      })
    );

    await expect(authApi.me()).rejects.toMatchObject({
      name: "ApiError",
      code: "INSUFFICIENT_BIRDS",
      status: 409,
      message: "The sale exceeds the available birds.",
      details: { available: 3 },
    });
  });
  it("turns backend connection failures into a readable recovery error", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(authApi.me()).rejects.toMatchObject({
      name: "ApiError",
      code: "BACKEND_UNAVAILABLE",
      status: 503,
      message: "The local backend is unavailable. Restart the application and try again.",
    });
  });

});
