import { useState } from "react";
import { CSV_TEMPLATES, downloadCsvTemplate, inspectCsvFile, csvErrorReport } from "../utils/csvImport.js";
import { csvImportApi, farmApi } from "../services/api.js";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";

export default function ImportExportPage() {
  const [importModule, setImportModule] = useState("customers");
  const [importPreview, setImportPreview] = useState(null);
  const [importFile, setImportFile] = useState(null);
  const [importBusy, setImportBusy] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [importError, setImportError] = useState("");

  async function inspectImport(event) {
    const file = event.target.files?.[0];
    setImportFile(file || null);
    setImportPreview(null);
    setImportError("");
    setImportMessage("");
    if (!file) return;
    try {
      const preview = await inspectCsvFile(file, importModule);
      if (importModule === "houses") {
        const existing = await farmApi.listHouses();
        const byCode = new Map(existing.map(item => [String(item.code || "").trim().toUpperCase(), item]));
        const byName = new Map(existing.map(item => [String(item.name || "").trim().toLocaleLowerCase(), item]));
        for (const row of preview.rows) {
          const codeMatch = byCode.get(row.record.code.trim().toUpperCase());
          const nameMatch = byName.get(row.record.name.trim().toLocaleLowerCase());
          if (!codeMatch && !nameMatch) continue;
          const exactMatch = codeMatch && nameMatch && codeMatch.id === nameMatch.id
            && codeMatch.name === row.record.name.trim()
            && String(codeMatch.notes || "").trim() === row.record.notes.trim();
          if (exactMatch) {
            row.existingDuplicate = true;
            preview.duplicates += 1;
          } else {
            row.errors.push(codeMatch ? "House/pen code conflicts with an existing record." : "House/pen name conflicts with an existing record.");
            row.valid = false;
          }
        }
        preview.validRows = preview.rows.filter(row => row.valid).length;
        preview.invalidRows = preview.rows.filter(row => !row.valid).length;
      }
      if (["egg-collections", "broiler-weights"].includes(importModule)) {
        const serverPreview = await csvImportApi.previewProductionRecords(importModule, preview.rows.map(row => row.record));
        for (let index = 0; index < preview.rows.length; index += 1) {
          const row = preview.rows[index];
          const checked = serverPreview.rows?.[index];
          if (!checked) { row.errors.push("The backend could not validate this row."); row.valid = false; continue; }
          row.errors = [...row.errors, ...(checked.errors || [])];
          row.duplicate = Boolean(checked.duplicate);
          row.existingDuplicate = Boolean(checked.duplicate);
          row.valid = Boolean(checked.valid) && row.errors.length === 0;
        }
        preview.duplicates = serverPreview.duplicates || 0;
        preview.validRows = preview.rows.filter(row => row.valid).length;
        preview.invalidRows = preview.rows.filter(row => !row.valid).length;
      }
      if (["health", "drugs", "vaccinations"].includes(importModule)) {
        const serverPreview = await csvImportApi.previewHealthRecords(importModule, preview.rows.map(row => row.record));
        for (let index = 0; index < preview.rows.length; index += 1) {
          const row = preview.rows[index];
          const checked = serverPreview.rows?.[index];
          if (!checked) { row.errors.push("The backend could not validate this row."); row.valid = false; continue; }
          row.errors = [...row.errors, ...(checked.errors || [])];
          row.duplicate = Boolean(checked.duplicate);
          row.existingDuplicate = Boolean(checked.duplicate);
          row.valid = Boolean(checked.valid) && row.errors.length === 0;
        }
        preview.duplicates = serverPreview.duplicates || 0;
        preview.validRows = preview.rows.filter(row => row.valid).length;
        preview.invalidRows = preview.rows.filter(row => !row.valid).length;
      }
      if (["feed-purchases", "feed-usage"].includes(importModule)) {
        const serverPreview = await csvImportApi.previewFeedRecords(importModule, preview.rows.map(row => row.record));
        for (let index = 0; index < preview.rows.length; index += 1) {
          const row = preview.rows[index];
          const checked = serverPreview.rows?.[index];
          if (!checked) { row.errors.push("The backend could not validate this row."); row.valid = false; continue; }
          row.errors = [...row.errors, ...(checked.errors || [])];
          row.duplicate = Boolean(checked.duplicate);
          row.existingDuplicate = Boolean(checked.duplicate);
          row.valid = Boolean(checked.valid) && row.errors.length === 0;
        }
        preview.duplicates = serverPreview.duplicates || 0;
        preview.validRows = preview.rows.filter(row => row.valid).length;
        preview.invalidRows = preview.rows.filter(row => !row.valid).length;
      }
      setImportPreview(preview);
    } catch (error) {
      setImportError(error.message || "Could not inspect this CSV file.");
    }
  }

  function downloadErrorReport() {
    if (!importPreview) return;
    const blob = new Blob(["\uFEFF", csvErrorReport(importPreview)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `pomas-${importModule}-import-errors.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  async function confirmImport() {
    if (!importPreview || importPreview.invalidRows || !importPreview.totalRows || importBusy) return;
    const confirmed = window.confirm(`Import ${importPreview.totalRows} ${CSV_TEMPLATES[importModule].label.toLowerCase()}? Existing exact matches will be skipped as duplicates. This will write records to this farm.`);
    if (!confirmed) return;
    setImportBusy(true);
    setImportError("");
    setImportMessage("");
    try {
      const records = importPreview.rows.map(row => row.record);
      const healthHistory = ["health", "drugs", "vaccinations"].includes(importModule);
      const feedHistory = ["feed-purchases", "feed-usage"].includes(importModule);
      const operationalHistory = ["daily-records", "mortality", "culling"].includes(importModule);
      const productionHistory = ["egg-collections", "broiler-weights"].includes(importModule);
      const result = productionHistory
        ? await csvImportApi.importProductionRecords(importModule, records)
        : feedHistory
          ? await csvImportApi.importFeedRecords(importModule, records)
          : healthHistory
          ? await csvImportApi.importHealthRecords(importModule, records)
          : operationalHistory
            ? await csvImportApi.importOperationalRecords(importModule, records)
            : await csvImportApi.importRecords(importModule, records);
      if (feedHistory) {
        const count = importModule === "feed-purchases" ? result.importedPurchases : result.importedUsage;
        const duplicateText = result.skippedDuplicates ? ` ${result.skippedDuplicates} exact duplicate(s) skipped.` : "";
        setImportMessage(`Imported ${count} ${CSV_TEMPLATES[importModule].label.toLowerCase()} successfully.${duplicateText}`);
      } else {
        const duplicateText = result.duplicates ? ` ${result.duplicates} exact duplicate(s) skipped.` : "";
        setImportMessage(`Imported ${result.imported} ${CSV_TEMPLATES[importModule].label.toLowerCase()} successfully.${duplicateText}`);
      }
      setImportPreview(null);
      setImportFile(null);
      const input = document.getElementById("pomas-csv-file");
      if (input) input.value = "";
    } catch (error) {
      setImportError(error.message || "Import failed. No success was reported.");
    } finally {
      setImportBusy(false);
    }
  }

  return (
    <PageTemplate title="Import / Export" description="Move supported data into Pomas or export the reports the application currently provides." className="grid">
      <section className="card full">
        <div className="section-head">
          <div>
            <p className="eyebrow">FARM DATA MIGRATION</p>
            <h2>Farm and batch records</h2>
          </div>
        </div>
        <p>Houses and pens can be imported first as the prerequisite for historical batch imports. Batch import itself is not yet available. Health/treatment, drug, vaccination, layer egg collections, and broiler weight history can be imported for existing active batches.</p>
        <div className="dashboard-columns">
          <div className="subsection">
            <h3>Houses and pens</h3>
            <p className="muted">Import order: houses/pens first, then batches. The template uses the existing name, code, and notes fields. Capacity fields are not included because they are not part of the current house/pen model.</p>
            <p className="muted">An exact existing record is skipped as a duplicate. Reusing a name or code with different details is a conflict. Existing records are never silently overwritten.</p>
          </div>
          <div className="subsection">
            <h3>Existing batch opening records</h3>
            <p className="muted">Planned. The import path must preserve the real placement date, opening bird count, house or pen, supplier, and original bird cost before it can be enabled.</p>
            <button type="button" disabled aria-disabled="true">Batch import unavailable</button>
            <p className="muted">For now, open the batch manually and enter its actual historical placement date and opening details.</p>
            <button type="button" onClick={() => { window.location.hash = "flocks/flocks"; }}>Open batch management</button>
          </div>
          <div className="subsection">
            <h3>Export farm or batch reports</h3>
            <p className="muted">CSV and PDF export are available for farm reports and batch reports. Exports use the selected report as-of date.</p>
            <button type="button" onClick={() => { window.location.hash = "reports/farm-report"; }}>Open report exports</button>
          </div>
        </div>
      </section>

      <section className="card full">
        <div className="section-head">
          <div>
            <p className="eyebrow">SUPPORTED REFERENCE DATA</p>
            <h2>CSV import</h2>
          </div>
        </div>
        <p className="muted">Download the versioned template, keep its headers unchanged, and preview the entire file before confirming. Feed imports are checked against configured feed types, supplier and batch references, existing transaction dates, unit rules, and dated FIFO stock. Purchase prices are entered in naira and converted by the backend to integer kobo. Confirmed feed records use the existing feed services in one database transaction. Unknown kilogram conversions remain unknown; do not infer bag weights. Health, drug, and vaccination previews validate batch references, placement dates, required fields, and numeric constraints without writing records. Drug records create the same DRUGS expense as manual entry.</p>
        <div className="form-grid">
          <label>Import module
            <select value={importModule} disabled={importBusy} onChange={event => {
              setImportModule(event.target.value);
              setImportPreview(null);
              setImportFile(null);
              setImportError("");
              setImportMessage("");
              const input = document.getElementById("pomas-csv-file");
              if (input) input.value = "";
            }}>
              <option value="houses">{CSV_TEMPLATES.houses.label}</option>
              <option value="feed-purchases">{CSV_TEMPLATES["feed-purchases"].label}</option>
              <option value="feed-usage">{CSV_TEMPLATES["feed-usage"].label}</option>
              <option value="egg-collections">{CSV_TEMPLATES["egg-collections"].label}</option>
              <option value="broiler-weights">{CSV_TEMPLATES["broiler-weights"].label}</option>
              <option value="health">{CSV_TEMPLATES.health.label}</option>
              <option value="drugs">{CSV_TEMPLATES.drugs.label}</option>
              <option value="vaccinations">{CSV_TEMPLATES.vaccinations.label}</option>
              <option value="customers">{CSV_TEMPLATES.customers.label}</option>
              <option value="suppliers">{CSV_TEMPLATES.suppliers.label}</option>
            </select>
          </label>
          <div className="inline-form">
            <button type="button" onClick={() => downloadCsvTemplate(importModule)}>Download {CSV_TEMPLATES[importModule].label.toLowerCase()} template (v{CSV_TEMPLATES[importModule].version})</button>
          </div>
          <label>Choose completed CSV
            <input id="pomas-csv-file" type="file" accept=".csv,text/csv" disabled={importBusy} onChange={inspectImport} />
            <small>UTF-8 CSV, maximum 2 MB and 5,000 data rows.</small>
          </label>
        </div>
        {importError && <p role="alert" className="error-message">{importError}</p>}
        {importMessage && <p role="status" className="success-message">{importMessage}</p>}
        {importPreview && <div className="subsection">
          <h3>Import preview</h3>
          <p>{importFile?.name} · Template v{importPreview.templateVersion}</p>
          <p>Total rows: <strong>{importPreview.totalRows}</strong> · Valid: <strong>{importPreview.validRows}</strong> · Invalid: <strong>{importPreview.invalidRows}</strong>{["houses", "health", "drugs", "vaccinations", "feed-purchases", "feed-usage"].includes(importModule) && <> · Exact duplicates: <strong>{importPreview.duplicates || 0}</strong></>}</p>
          {importPreview.invalidRows > 0 && <button type="button" onClick={downloadErrorReport}>Download row error report</button>}
          <div className="table-scroll">
            <table>
              <thead><tr><th>CSV row</th>{CSV_TEMPLATES[importModule].headers.map(header => <th key={header}>{header}</th>)}<th>Validation</th></tr></thead>
              <tbody>{importPreview.rows.slice(0, 100).map(item => <tr key={item.rowNumber}><td>{item.rowNumber}</td>{CSV_TEMPLATES[importModule].headers.map(header => <td key={header}>{item.record[header]}</td>)}<td>{item.errors.join(" ") || (item.duplicate || item.existingDuplicate ? "Exact duplicate; will be skipped" : "Valid")}</td></tr>)}</tbody>
            </table>
          </div>
          {importPreview.rows.length > 100 && <p className="muted">Showing first 100 rows. The import validates all {importPreview.totalRows} rows.</p>}
          <button type="button" disabled={importBusy || !importPreview.totalRows || importPreview.invalidRows > 0} onClick={confirmImport}>{importBusy ? "Importing…" : `Confirm import (${importPreview.validRows})`}</button>
        </div>}
      </section>
    </PageTemplate>
  );
}
