import { useState } from "react";
import { CSV_TEMPLATES, downloadCsvTemplate, inspectCsvFile, csvErrorReport } from "../utils/csvImport.js";
import { csvImportApi } from "../services/api.js";
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
      if (["daily-records", "mortality", "culling"].includes(importModule)) {
        const serverPreview = await csvImportApi.previewOperationalRecords(importModule, preview.rows.map(row => row.record));
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
    const confirmed = window.confirm(`Import ${importPreview.totalRows} ${CSV_TEMPLATES[importModule].label.toLowerCase()}? This will write records to this farm.`);
    if (!confirmed) return;
    setImportBusy(true);
    setImportError("");
    setImportMessage("");
    try {
      const operational = ["daily-records", "mortality", "culling"].includes(importModule);
            const result = operational
              ? await csvImportApi.importOperationalRecords(importModule, importPreview.rows.map(row => row.record))
              : await csvImportApi.importRecords(importModule, importPreview.rows.map(row => row.record));
      setImportMessage(`Imported ${result.imported} ${CSV_TEMPLATES[importModule].label.toLowerCase()} successfully.`);
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
        <p>Import order: create the batch with its real placement date and opening bird count first, then import dated daily records, mortality, and culling. Operational imports only accept existing ACTIVE batches and never invent missing days.</p>
        <div className="dashboard-columns">
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
        <p className="muted">Download the versioned template, keep its headers unchanged, and preview the full file before confirming. Operational preview checks batch references, placement dates, configured water containers, and the historical bird-population sequence without writing records. Confirmed imports use the existing audited services in a database transaction. Feed remains in the feed subsystem.</p>
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
              <option value="daily-records">{CSV_TEMPLATES["daily-records"].label}</option>
              <option value="mortality">{CSV_TEMPLATES.mortality.label}</option>
              <option value="culling">{CSV_TEMPLATES.culling.label}</option>
              <option value="customers">{CSV_TEMPLATES.customers.label}</option>
              <option value="suppliers">{CSV_TEMPLATES.suppliers.label}</option>
            </select>
          </label>
          <div className="inline-form">
            <button type="button" onClick={() => downloadCsvTemplate(importModule)}>Download {CSV_TEMPLATES[importModule].label.toLowerCase()} template</button>
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
          <p>Total rows: <strong>{importPreview.totalRows}</strong> · Valid: <strong>{importPreview.validRows}</strong> · Invalid: <strong>{importPreview.invalidRows}</strong>{["daily-records", "mortality", "culling"].includes(importModule) && <> · Exact duplicates to skip: <strong>{importPreview.duplicates || 0}</strong></>}</p>
          {importPreview.invalidRows > 0 && <button type="button" onClick={downloadErrorReport}>Download row error report</button>}
          <div className="table-scroll">
            <table>
              <thead><tr><th>CSV row</th>{CSV_TEMPLATES[importModule].headers.map(header => <th key={header}>{header}</th>)}<th>Validation</th></tr></thead>
              <tbody>{importPreview.rows.slice(0, 100).map(item => <tr key={item.rowNumber}><td>{item.rowNumber}</td>{CSV_TEMPLATES[importModule].headers.map(header => <td key={header}>{item.record[header]}</td>)}<td>{item.duplicate || item.existingDuplicate ? "Exact duplicate; will be skipped" : item.valid ? "Valid" : item.errors.join(" ")}</td></tr>)}</tbody>
            </table>
          </div>
          {importPreview.rows.length > 100 && <p className="muted">Showing first 100 rows. The import validates all {importPreview.totalRows} rows.</p>}
          <button type="button" disabled={importBusy || !importPreview.totalRows || importPreview.invalidRows > 0} onClick={confirmImport}>{importBusy ? "Importing…" : `Confirm import (${importPreview.validRows})`}</button>
        </div>}
      </section>
    </PageTemplate>
  );
}
