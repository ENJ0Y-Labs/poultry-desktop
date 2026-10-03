import { describe, expect, it, beforeEach } from "vitest";
import { todayLocalDate, getLastBatchId, rememberLastBatchId } from "./farmUx.js";

describe("farm UX helpers", () => {
  beforeEach(() => localStorage.clear());

  it("returns a local calendar date instead of relying on UTC conversion", () => {
    const value = todayLocalDate();
    expect(value).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(value).toBe(
      `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}-${String(new Date().getDate()).padStart(2, "0")}`,
    );
  });

  it("remembers the last-used batch", () => {
    expect(getLastBatchId()).toBe("");
    rememberLastBatchId("batch-123");
    expect(getLastBatchId()).toBe("batch-123");
  });

  it("ignores an empty batch id", () => {
    rememberLastBatchId("");
    expect(getLastBatchId()).toBe("");
  });
});
