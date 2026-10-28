const MAX_FILE_BYTES = 2 * 1024 * 1024;
const MAX_DATA_ROWS = 5000;
const MAX_CELL_CHARS = 10_000;

export const CSV_TEMPLATES = {
  customers: {
    label: "Customers",
    version: "1.0",
    headers: ["name", "phone", "notes"],
    required: ["name"],
    example: ["Example Customer", "08000000000", "Replace or remove this example row"],
  },
  suppliers: {
    label: "Suppliers",
    version: "1.0",
    headers: ["name", "phone", "email", "address", "notes"],
    required: ["name"],
    example: ["Example Feed Supplier", "08000000000", "supplier@example.test", "Example address", "Replace or remove this example row"],
  },
};

export function encodeCsv(rows) {
  return rows.map(row => row.map(value => {
    // Keep generated reports safe to open in spreadsheet software. Imported
    // values are data, never formulas.
    let text = String(value ?? "");
    if (/^[\u0000-\u0020]*[=+@-]/.test(text)) text = "'" + text;
    return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
  }).join(",")).join("\r\n") + "\r\n";
}

export function downloadCsvTemplate(moduleKey) {
  const template = CSV_TEMPLATES[moduleKey];
  if (!template) throw new Error("Choose a supported import module.");
  const csv = encodeCsv([template.headers, template.example]);
  const blob = new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `pomas-${moduleKey}-template-v${template.version}.csv`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function parseCsv(text) {
  const source = String(text ?? "").replace(/^\uFEFF/, "");
  const rows = [];
  let row = [], cell = "", quoted = false, closedQuote = false;
  for (let i = 0; i < source.length; i += 1) {
    const char = source[i];
    if (quoted) {
      if (char === '"') {
        if (source[i + 1] === '"') { cell += '"'; i += 1; }
        else { quoted = false; closedQuote = true; }
      } else cell += char;
      if (cell.length > MAX_CELL_CHARS) throw new Error(`CSV cell exceeds the ${MAX_CELL_CHARS.toLocaleString()} character limit.`);
      continue;
    }
    if (closedQuote && char !== "," && char !== "\r" && char !== "\n" && char !== " " && char !== "\t") {
      throw new Error(`Malformed CSV quoting near character ${i + 1}.`);
    }
    if (char === '"') {
      if (cell.length !== 0 || closedQuote) throw new Error(`Malformed CSV quoting near character ${i + 1}.`);
      quoted = true;
    } else if (char === ",") { row.push(cell); cell = ""; closedQuote = false; }
    else if (char === "\r" || char === "\n") {
      if (char === "\r" && source[i + 1] === "\n") i += 1;
      row.push(cell); cell = ""; closedQuote = false;
      if (row.some(value => value.trim() !== "")) rows.push(row);
      row = [];
      if (rows.length > MAX_DATA_ROWS + 1) throw new Error(`CSV exceeds the ${MAX_DATA_ROWS} data-row limit.`);
    } else if (!closedQuote) cell += char;
    if (cell.length > MAX_CELL_CHARS) throw new Error(`CSV cell exceeds the ${MAX_CELL_CHARS.toLocaleString()} character limit.`);
  }
  if (quoted) throw new Error("Malformed CSV: a quoted cell was not closed.");
  if (cell.length || row.length || closedQuote) {
    row.push(cell);
    if (row.some(value => value.trim() !== "")) rows.push(row);
  }
  if (rows.length > MAX_DATA_ROWS + 1) throw new Error(`CSV exceeds the ${MAX_DATA_ROWS} data-row limit.`);
  return rows;
}

async function readUtf8File(file) {
  if (typeof file.arrayBuffer === "function") {
    let decoded;
    try {
      decoded = new TextDecoder("utf-8", { fatal: true }).decode(await file.arrayBuffer());
    } catch {
      throw new Error("The CSV file is not valid UTF-8. Save it as UTF-8 and try again.");
    }
    return decoded;
  }
  // Test doubles and older File-like integrations may expose text() only.
  return file.text();
}

export async function inspectCsvFile(file, moduleKey) {
  if (!file) throw new Error("Choose a CSV file first.");
  if (!Number.isFinite(file.size) || file.size > MAX_FILE_BYTES) throw new Error("CSV file exceeds the 2 MB limit.");
  const template = CSV_TEMPLATES[moduleKey];
  if (!template) throw new Error("Choose a supported import module.");
  const rows = parseCsv(await readUtf8File(file));
  if (!rows.length) throw new Error("The CSV file is empty.");
  const headers = rows[0].map(value => value.trim());
  const normalizedHeaders = headers.map(value => value.toLocaleLowerCase("en"));
  if (normalizedHeaders.some(value => !value)) throw new Error("The CSV contains an empty column header.");
  if (new Set(normalizedHeaders).size !== normalizedHeaders.length) throw new Error("The CSV contains duplicate column headers.");
  const templateHeaders = template.headers.map(value => value.toLocaleLowerCase("en"));
  const missing = template.headers.filter(header => !normalizedHeaders.includes(header.toLocaleLowerCase("en")));
  const unexpected = headers.filter(header => !templateHeaders.includes(header.toLocaleLowerCase("en")));
  if (missing.length || unexpected.length) {
    throw new Error([missing.length ? `Missing columns: ${missing.join(", ")}.` : "", unexpected.length ? `Unexpected columns: ${unexpected.join(", ")}.` : ""].filter(Boolean).join(" "));
  }
  if (headers.length !== template.headers.length) throw new Error("CSV headers do not match this template.");
  const indexes = Object.fromEntries(normalizedHeaders.map((header, index) => [header, index]));
  const seen = new Set();
  const records = rows.slice(1).map((values, index) => {
    const record = Object.fromEntries(template.headers.map(header => [header, (values[indexes[header]] ?? "").trim()]));
    const errors = [];
    if (values.length !== headers.length) errors.push(`Expected ${headers.length} columns, found ${values.length}.`);
    for (const required of template.required) if (!record[required]) errors.push(`${required} is required.`);
    const key = record.name.toLocaleLowerCase("en");
    const duplicate = key && seen.has(key);
    if (key) seen.add(key);
    if (duplicate) errors.push("Duplicate name within this file.");
    return { rowNumber: index + 2, record, errors, duplicate: Boolean(duplicate), valid: errors.length === 0 };
  });
  return {
    moduleKey,
    templateVersion: template.version,
    totalRows: records.length,
    validRows: records.filter(row => row.valid).length,
    invalidRows: records.filter(row => !row.valid).length,
    duplicateRows: records.filter(row => row.duplicate).length,
    rows: records,
  };
}

export function csvErrorReport(preview) {
  const rows = [["row", "status", "errors", ...CSV_TEMPLATES[preview.moduleKey].headers]];
  for (const item of preview.rows) {
    rows.push([item.rowNumber, item.valid ? "valid" : "invalid", item.errors.join("; "), ...CSV_TEMPLATES[preview.moduleKey].headers.map(header => item.record[header])]);
  }
  return encodeCsv(rows);
}

export const CSV_IMPORT_LIMITS = { maxFileBytes: MAX_FILE_BYTES, maxDataRows: MAX_DATA_ROWS, maxCellChars: MAX_CELL_CHARS };
