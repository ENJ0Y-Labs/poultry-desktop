import { describe, expect, it } from "vitest";
import { formatAuditAction, formatAuditEntity, formatAuditTimestamp } from "./auditFormatting.js";

describe("formatAuditTimestamp", () => {
  it("formats ISO timestamps with nanoseconds in the farm timezone", () => {
    expect(formatAuditTimestamp("2026-10-26T08:21:44.123456789Z", "Africa/Lagos"))
      .toBe("26 Oct 2026, 09:21:44");
  });

  it("handles missing and invalid timestamps safely", () => {
    expect(formatAuditTimestamp(null)).toBe("—");
    expect(formatAuditTimestamp("")).toBe("—");
    expect(formatAuditTimestamp("not-a-timestamp")).toBe("Unknown time");
  });

  it("falls back safely when the configured timezone is invalid", () => {
    expect(formatAuditTimestamp("2026-10-26T08:21:44Z", "Not/A_Timezone"))
      .toBe("26 Oct 2026, 08:21:44 UTC");
  });
});

describe("formatAuditAction", () => {
  it("humanizes known audit actions", () => {
    expect(formatAuditAction("CREATE")).toBe("Created");
    expect(formatAuditAction("UPDATE")).toBe("Updated");
  });

  it("humanizes unknown actions and handles missing values", () => {
    expect(formatAuditAction("FARM_PROFILE_REBUILT")).toBe("Farm Profile Rebuilt");
    expect(formatAuditAction("")).toBe("Unknown action");
    expect(formatAuditAction(null)).toBe("Unknown action");
  });
});

describe("formatAuditEntity", () => {
  it("humanizes common entities", () => {
    expect(formatAuditEntity("FARM")).toBe("Farm");
    expect(formatAuditEntity("FARM_SETTINGS")).toBe("Farm settings");
  });

  it("humanizes unknown entities and handles missing values", () => {
    expect(formatAuditEntity("NEW_RECORD_KIND")).toBe("New Record Kind");
    expect(formatAuditEntity(undefined)).toBe("Unknown item");
  });
});
