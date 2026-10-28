import { describe, expect, it } from "vitest";
import { encodeCsv, inspectCsvFile, parseCsv } from "./csvImport.js";

describe("parseCsv", () => {
  it("supports BOM, quoted commas, escaped quotes, and CRLF", () => {
    expect(parseCsv('\uFEFFname,notes\r\n"Smith, Ltd","said ""hello"""\r\n')).toEqual([
      ["name", "notes"], ["Smith, Ltd", 'said "hello"'],
    ]);
  });
  it("ignores blank lines and preserves empty cells", () => {
    expect(parseCsv("a,b\n\n1,\n")).toEqual([["a", "b"], ["1", ""]]);
  });
  it("rejects unterminated and malformed quoted cells", () => {
    expect(() => parseCsv('name\n"unfinished')).toThrow(/not closed/);
    expect(() => parseCsv('"a"x,b')).toThrow(/Malformed CSV quoting/);
  });
  it("encodes commas, quotes, and line breaks using CSV quoting", () => {
    expect(encodeCsv([["a", "b"], ["x,y", 'say "hi"']])).toBe('a,b\r\n"x,y","say ""hi"""\r\n');
  });
});

describe("inspectCsvFile", () => {
  it("reports required-field and duplicate-name errors with source row numbers", async () => {
    const file = new File(["name,phone,notes\nAda,1,\nada,2,\n,3,\n"], "customers.csv", { type: "text/csv" });
    const result = await inspectCsvFile(file, "customers");
    expect(result.totalRows).toBe(3);
    expect(result.validRows).toBe(1);
    expect(result.invalidRows).toBe(2);
    expect(result.rows[1].errors).toContain("Duplicate name within this file.");
    expect(result.rows[2].errors).toContain("name is required.");
  });
  it("rejects missing and unexpected headers", async () => {
    const file = new File(["name,other\nAda,x\n"], "customers.csv");
    await expect(inspectCsvFile(file, "customers")).rejects.toThrow(/Missing columns.*Unexpected columns/);
  });
});
