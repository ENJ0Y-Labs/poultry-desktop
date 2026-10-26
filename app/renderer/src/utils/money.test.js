import { describe, expect, it } from "vitest";
import { formatMoney } from "./money.js";

describe("formatMoney", () => {
  it.each([
    [0, "₦0.00"],
    [1, "₦0.01"],
    [1234, "₦12.34"],
    [100000, "₦1,000.00"],
    [123456789, "₦1,234,567.89"],
    [-1234, "-₦12.34"],
  ])("formats %s minor units as readable currency", (minor, expected) => {
    expect(formatMoney(minor)).toBe(expected);
  });

  it("uses a supplied currency code", () => {
    expect(formatMoney(1234, "USD")).toContain("12.34");
    expect(formatMoney(1234, "USD")).toContain("US$");
  });

  it.each([null, undefined])("renders missing amounts as a dash (%s)", value => {
    expect(formatMoney(value)).toBe("—");
  });
});
