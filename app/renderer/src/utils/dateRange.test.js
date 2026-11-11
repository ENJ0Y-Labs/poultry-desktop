import { describe, expect, it } from "vitest";
import { createDateRange, formatDateRange, isValidDateRange } from "./dateRange.js";

const fixedNow = new Date("2024-03-01T12:00:00.000Z");

describe("date range presets", () => {
  it("uses the farm timezone rather than the computer timezone to determine today", () => {
    const range = createDateRange("TODAY", "Africa/Lagos", new Date("2024-02-29T23:30:00.000Z"));
    expect(range).toEqual({ preset: "TODAY", from: "2024-03-01", to: "2024-03-01" });
  });

  it("uses inclusive boundaries for rolling periods", () => {
    expect(createDateRange("LAST_7_DAYS", "UTC", fixedNow)).toMatchObject({ from: "2024-02-24", to: "2024-03-01" });
    expect(createDateRange("LAST_30_DAYS", "UTC", fixedNow)).toMatchObject({ from: "2024-02-01", to: "2024-03-01" });
    expect(createDateRange("LAST_90_DAYS", "UTC", fixedNow)).toMatchObject({ from: "2023-12-03", to: "2024-03-01" });
  });

  it("handles yesterday, month boundaries, year boundaries and leap day", () => {
    expect(createDateRange("YESTERDAY", "UTC", fixedNow)).toMatchObject({ from: "2024-02-29", to: "2024-02-29" });
    expect(createDateRange("THIS_MONTH", "UTC", fixedNow)).toMatchObject({ from: "2024-03-01", to: "2024-03-01" });
    expect(createDateRange("LAST_MONTH", "UTC", fixedNow)).toMatchObject({ from: "2024-02-01", to: "2024-02-29" });
    expect(createDateRange("THIS_YEAR", "UTC", new Date("2025-01-01T12:00:00.000Z"))).toMatchObject({ from: "2025-01-01", to: "2025-01-01" });
  });

  it("rejects incomplete, impossible and reversed custom ranges", () => {
    expect(isValidDateRange({ from: "2024-02-29", to: "2024-03-01" })).toBe(true);
    expect(isValidDateRange({ from: "2024-03-02", to: "2024-03-01" })).toBe(false);
    expect(isValidDateRange({ from: "2024-02-30", to: "2024-03-01" })).toBe(false);
    expect(isValidDateRange({ from: "", to: "2024-03-01" })).toBe(false);
  });

  it("formats an inclusive range for display", () => {
    expect(formatDateRange({ from: "2024-02-29", to: "2024-03-01" })).toBe("29 Feb 2024 – 01 Mar 2024");
  });
});
