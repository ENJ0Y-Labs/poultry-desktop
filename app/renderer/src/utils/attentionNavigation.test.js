import { describe, expect, it } from "vitest";
import { attentionTarget, parseAttentionContext } from "./attentionNavigation.js";

describe("attentionTarget", () => {
  it.each([
    [{ type: "MISSING_DAILY_RECORD", batchId: "batch 1", date: "2026-10-26" }, "#daily-work/daily-record/batchId=batch%201/date=2026-10-26"],
    [{ type: "HIGH_MORTALITY", batchId: "batch-1" }, "#flocks/batch-dashboard/batchId=batch-1"],
    [{ type: "VACCINATION_DUE", batchId: "batch-1" }, "#health/vaccination/batchId=batch-1"],
    [{ type: "UNUSUAL_PRODUCTION_DROP", batchId: "batch-1" }, "#production/eggs/batchId=batch-1"],
    [{ type: "BATCH_NEARING_SALE", batchId: "batch-1" }, "#production/broiler/batchId=batch-1"],
    [{ type: "LOW_FEED_STOCK", feedTypeId: "feed-1" }, "#feed-stock/purchases/feedTypeId=feed-1"],
    [{ type: "LOW_INVENTORY", itemId: "item-1" }, "#feed-stock/inventory/itemId=item-1"],
    [{ type: "INSUFFICIENT_INVENTORY", itemId: "item-1" }, "#feed-stock/inventory/itemId=item-1"],
    [{ type: "BACKUP_OVERDUE" }, "#settings/backup"],
  ])("maps %o to its contextual destination", (alert, expected) => {
    expect(attentionTarget(alert)).toBe(expected);
  });

  it.each([
    null,
    {},
    { type: "UNKNOWN_ALERT" },
    { type: "MISSING_DAILY_RECORD", batchId: "batch-1" },
    { type: "HIGH_MORTALITY" },
    { type: "LOW_FEED_STOCK", item: "Layer Mash" },
    { type: "LOW_INVENTORY", item: "Vaccine" },
  ])("rejects missing or unsupported context: %o", alert => {
    expect(attentionTarget(alert)).toBeNull();
  });
});

describe("parseAttentionContext", () => {
  it("decodes supported context without accepting arbitrary keys", () => {
    expect(parseAttentionContext("#daily-work/daily-record/batchId=batch%201/date=2026-10-26/admin=true"))
      .toEqual({ batchId: "batch 1", date: "2026-10-26" });
  });

  it("ignores malformed encoded context", () => {
    expect(parseAttentionContext("#health/vaccination/batchId=%E0%A4%A"))
      .toEqual({});
  });
});
