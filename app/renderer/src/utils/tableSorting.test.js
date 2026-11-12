import { describe, expect, it } from "vitest";
import { paginateTableRows, sortTableRows } from "./tableSorting.js";
const columns = [{ key: "name", label: "Name" }, { key: "amount", label: "Amount" }];
describe("sortTableRows", () => {
  it("sorts ascending and descending", () => {
    const rows = [{ id: "2", name: "Beta" }, { id: "1", name: "Alpha" }];
    expect(sortTableRows(rows, { key: "name", direction: "asc" }, columns).map(x => x.name)).toEqual(["Alpha", "Beta"]);
    expect(sortTableRows(rows, { key: "name", direction: "desc" }, columns).map(x => x.name)).toEqual(["Beta", "Alpha"]);
  });
  it("uses an ID tie-breaker in the same direction for either primary sort", () => {
    const rows = [{ id: "b", amount: 4 }, { id: "a", amount: 4 }, { id: "c", amount: 3 }];
    expect(sortTableRows(rows, { key: "amount", direction: "asc" }, columns).map(x => x.id)).toEqual(["c", "a", "b"]);
    expect(sortTableRows(rows, { key: "amount", direction: "desc" }, columns).map(x => x.id)).toEqual(["a", "b", "c"]);
  });
  it("sorts numeric values numerically and keeps nulls last", () => {
    const rows = [{ id: "1", amount: "10" }, { id: "2", amount: "2" }, { id: "3", amount: null }];
    expect(sortTableRows(rows, { key: "amount", direction: "asc" }, columns).map(x => x.id)).toEqual(["2", "1", "3"]);
  });
  it("does not mutate source rows", () => {
    const rows = [{ id: "2", name: "B" }, { id: "1", name: "A" }];
    sortTableRows(rows, { key: "name", direction: "asc" }, columns);
    expect(rows.map(x => x.id)).toEqual(["2", "1"]);
  });
});
describe("paginateTableRows", () => {
  it("handles empty and single-page sets", () => {
    expect(paginateTableRows([], 0, 10)).toEqual([]);
    expect(paginateTableRows([{ id: 1 }], 0, 10)).toEqual([{ id: 1 }]);
  });
  it("supports 10, 25, 50 and 100 row pages", () => {
    const rows = Array.from({ length: 120 }, (_, id) => ({ id }));
    expect(paginateTableRows(rows, 1, 10)).toHaveLength(10);
    expect(paginateTableRows(rows, 0, 25)).toHaveLength(25);
    expect(paginateTableRows(rows, 0, 50)).toHaveLength(50);
    expect(paginateTableRows(rows, 0, 100)).toHaveLength(100);
  });
  it("clamps an out-of-range page after records are removed", () => {
    expect(paginateTableRows([{ id: 10 }], 8, 10)).toEqual([{ id: 10 }]);
  });
});
