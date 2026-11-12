import { describe, expect, it } from "vitest";
import { filterTableRows } from "./tableFilters.js";

const rows = [
  { id: "1", name: "Layer Mash", batchId: "b1", batchCode: "L-001", houseId: "h1", houseName: "House A", status: "ACTIVE", recordDate: "2026-10-01" },
  { id: "2", name: "Broiler Starter", batchId: "b2", batchCode: "B-002", houseId: "h2", houseName: "House B", status: "SOLD", recordDate: "2026-10-15" },
  { id: "3", name: "Layer Mash", batchId: "b1", batchCode: "L-001", houseId: "h2", houseName: "House B", status: "ACTIVE", recordDate: "2026-11-01" },
];
const fields = { dateKey: "recordDate", batchKey: "batchId", houseKey: "houseId", statusKey: "status" };

describe("filterTableRows", () => {
  it("intersects search, batch, house, status and date filters", () => {
    const result = filterTableRows(rows, { query: "layer", batch: "b1", house: "h2", status: "ACTIVE", from: "2026-10-20", to: "2026-11-01" }, fields);
    expect(result.map(row => row.id)).toEqual(["3"]);
  });
  it("includes both date boundaries", () => {
    expect(filterTableRows(rows, { ...{ query: "", batch: "", house: "", status: "" }, from: "2026-10-01", to: "2026-10-15" }, fields).map(row => row.id)).toEqual(["1", "2"]);
  });
  it("returns an empty array for no matches without throwing", () => {
    expect(filterTableRows(rows, { query: "", from: "", to: "", batch: "missing", house: "", status: "" }, fields)).toEqual([]);
  });
  it("does not exclude undated rows when no date range is selected", () => {
    expect(filterTableRows([{ id: "x", name: "No date" }], { query: "", from: "", to: "", batch: "", house: "", status: "" }, {})).toHaveLength(1);
  });
  it("does not mutate source rows", () => {
    const before = structuredClone(rows);
    filterTableRows(rows, { query: "layer", from: "", to: "", batch: "", house: "", status: "" }, fields);
    expect(rows).toEqual(before);
  });
});
