import { describe, expect, it } from "vitest";
import { formatEggQuantity } from "./eggFormatting.js";

describe("formatEggQuantity", () => {
  it("formats a crate total with a remainder", () => {
    expect(formatEggQuantity(921, 30)).toBe("30 crates, 21 eggs");
  });

  it("shows crates only when there is no remainder", () => {
    expect(formatEggQuantity(900, 30)).toBe("30 crates");
  });

  it("shows individual eggs below one crate", () => {
    expect(formatEggQuantity(12, 30)).toBe("12 eggs");
  });

  it("uses singular labels", () => {
    expect(formatEggQuantity(31, 30)).toBe("1 crate, 1 egg");
    expect(formatEggQuantity(1, 30)).toBe("1 egg");
  });

  it("shows zero clearly", () => {
    expect(formatEggQuantity(0, 30)).toBe("0 eggs");
  });

  it("uses the farm's configured crate size rather than assuming 30", () => {
    expect(formatEggQuantity(25, 12)).toBe("2 crates, 1 egg");
  });

  it("falls back to individual eggs when the crate size is unavailable", () => {
    expect(formatEggQuantity(31, null)).toBe("31 eggs");
    expect(formatEggQuantity(31, 0)).toBe("31 eggs");
  });

  it("does not format missing or invalid egg totals as zero", () => {
    expect(formatEggQuantity(null, 30)).toBe("—");
    expect(formatEggQuantity(Number.NaN, 30)).toBe("—");
    expect(formatEggQuantity(-1, 30)).toBe("—");
  });
});
