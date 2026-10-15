import { describe, expect, it } from "vitest";
import { parseNairaToKobo } from "./moneyInput.js";

describe("parseNairaToKobo", () => {
  it.each([
    ["0", 0],
    ["1", 100],
    ["125", 12500],
    ["12.3", 1230],
    ["12.34", 1234],
    ["0004.05", 405],
  ])("converts naira string %s to integer kobo", (input, expected) => {
    expect(parseNairaToKobo(input)).toBe(expected);
    expect(Number.isInteger(parseNairaToKobo(input))).toBe(true);
  });

  it.each(["", " ", "-1", "1.234", "1,000", "NaN", "1.", ".50"])(
    "rejects invalid money string %s",
    input => expect(() => parseNairaToKobo(input)).toThrow(),
  );

  it("rejects values outside the safe integer range", () => {
    expect(() => parseNairaToKobo("100000000000000")).toThrow(/too large/);
  });
});
