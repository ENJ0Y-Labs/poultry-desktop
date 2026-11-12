import { describe, expect, it } from "vitest";
import { calculatePeriodProfitMinor } from "./reportMetrics.js";

describe("calculatePeriodProfitMinor", () => {
  it("calculates profit from integer minor-unit totals", () => {
    expect(calculatePeriodProfitMinor(3_000, 1_000)).toBe(2_000);
    expect(calculatePeriodProfitMinor(1_000, 3_000)).toBe(-2_000);
  });

  it("does not turn missing or unsafe values into zero", () => {
    expect(calculatePeriodProfitMinor(null, 1_000)).toBeNull();
    expect(calculatePeriodProfitMinor(3_000, undefined)).toBeNull();
    expect(calculatePeriodProfitMinor(Number.MAX_SAFE_INTEGER + 1, 1)).toBeNull();
  });
});
