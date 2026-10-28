import { describe, expect, it } from "vitest";
import { CSV_TEMPLATES, encodeCsv, inspectCsvFile, parseCsv } from "./csvImport.js";

function csvFile(content, name = "customers.csv") {
  const bytes = new TextEncoder().encode(content);
  return {
    name,
    size: bytes.length,
    text: async () => content,
    arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  };
}

describe("parseCsv", () => {
  it("supports BOM, quoted commas, escaped quotes, embedded newlines, and CRLF", () => {
    expect(parseCsv('\uFEFFname,notes\r\n"Smith, Ltd","said ""hello""\r\nagain"\r\n')).toEqual([
      ["name", "notes"], ["Smith, Ltd", 'said "hello"\r\nagain'],
    ]);
  });

  it("ignores blank lines and preserves empty cells including a trailing empty cell", () => {
    expect(parseCsv("a,b\n\n1,\n2,")).toEqual([["a", "b"], ["1", ""], ["2", ""]]);
  });

  it("rejects unterminated, misplaced, and malformed quoted cells", () => {
    expect(() => parseCsv('name\n"unfinished')).toThrow(/not closed/);
    expect(() => parseCsv('"a"x,b')).toThrow(/Malformed CSV quoting/);
    expect(() => parseCsv('ab"cd,ef')).toThrow(/Malformed CSV quoting/);
  });

  it("encodes commas, quotes, and line breaks using CSV quoting", () => {
    expect(encodeCsv([["a", "b"], ["x,y", 'say "hi"']])).toBe('a,b\r\n"x,y","say ""hi"""\r\n');
  });

  it("neutralizes spreadsheet formulas in generated error reports", () => {
    expect(encodeCsv([["=HYPERLINK(\"https://example.test\")", "-1", "+2", "@SUM(A1)"]]))
      .toBe('"\'=HYPERLINK(""https://example.test"")",\'-1,\'+2,\'@SUM(A1)\r\n');
  });

  it("rejects cells larger than the configured limit", () => {
    expect(() => parseCsv("x".repeat(10_001))).toThrow(/character limit/);
  });
});

describe("inspectCsvFile", () => {
  it("reports required-field and duplicate-name errors with source row numbers", async () => {
    const file = csvFile("name,phone,notes\nAda,1,\nada,2,\n,3,\n");
    const result = await inspectCsvFile(file, "customers");
    expect(result.totalRows).toBe(3);
    expect(result.validRows).toBe(1);
    expect(result.invalidRows).toBe(2);
    expect(result.duplicateRows).toBe(1);
    expect(result.rows[1].errors).toContain("Duplicate name within this file.");
    expect(result.rows[2].errors).toContain("name is required.");
  });

  it("accepts header casing differences while mapping fields to the template", async () => {
    const result = await inspectCsvFile(csvFile("NAME,Phone,Notes\nAda,1,ok\n"), "customers");
    expect(result.validRows).toBe(1);
    expect(result.rows[0].record).toEqual({ name: "Ada", phone: "1", notes: "ok" });
  });

  it("rejects duplicate headers regardless of case and rejects missing/unexpected headers", async () => {
    await expect(inspectCsvFile(csvFile("name,NAME,notes\nAda,Ada,ok\n"), "customers"))
      .rejects.toThrow(/duplicate column headers/i);
    await expect(inspectCsvFile(csvFile("name,other\nAda,x\n"), "customers"))
      .rejects.toThrow(/Missing columns.*Unexpected columns/);
  });

  it("rejects invalid UTF-8 instead of silently replacing invalid bytes", async () => {
    const invalid = new Uint8Array([0x6e, 0x61, 0x6d, 0x65, 0x2c, 0xff]);
    const file = { name: "bad.csv", size: invalid.length, arrayBuffer: async () => invalid.buffer };
    await expect(inspectCsvFile(file, "customers")).rejects.toThrow(/not valid UTF-8/);
  });

  it("rejects oversized files and unknown modules", async () => {
    await expect(inspectCsvFile({ size: 2 * 1024 * 1024 + 1 }, "customers"))
      .rejects.toThrow(/2 MB limit/);
    await expect(inspectCsvFile(csvFile("name\nAda\n"), "batches"))
      .rejects.toThrow(/supported import module/);
  });
});
