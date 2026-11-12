import { useMemo, useState } from "react";
import { filterTableRows } from "../utils/tableFilters.js";

const EMPTY = { query: "", from: "", to: "", batch: "", house: "", status: "" };
function loadFilters(key) {
  try { return { ...EMPTY, ...JSON.parse(window.localStorage.getItem(key) || "{}") }; }
  catch { return EMPTY; }
}
function FilterChip({ label, onRemove }) {
  return <button type="button" className="table-filter-chip" onClick={onRemove}>{label} <span aria-hidden="true">×</span><span className="sr-only">Remove filter</span></button>;
}
export default function TableFilters({ rows, storageKey, onRowsChange }) {
  const fields = useMemo(() => ({
    dateKey: ["recordDate", "occurredDate", "saleDate", "purchaseDate", "date", "effectiveDate", "vaccinationDate", "administeredDate", "createdAt"].find(key => rows.some(row => row?.[key] != null)),
    batchKey: ["batchId", "batchCode"].find(key => rows.some(row => row?.[key] != null)),
    houseKey: ["houseId", "houseName", "houseCode"].find(key => rows.some(row => row?.[key] != null)),
    statusKey: ["status", "state"].find(key => rows.some(row => row?.[key] != null)),
  }), [rows]);
  const [filters, setFilters] = useState(() => loadFilters(storageKey));
  const filteredRows = useMemo(() => filterTableRows(rows, filters, fields), [rows, filters, fields]);
  const update = (key, value) => {
    const next = { ...filters, [key]: value };
    setFilters(next);
    try { window.localStorage.setItem(storageKey, JSON.stringify(next)); } catch { /* Storage is optional. */ }
  };
  const clear = () => {
    setFilters(EMPTY);
    try { window.localStorage.removeItem(storageKey); } catch { /* Storage is optional. */ }
  };
  const options = (key) => [...new Map(rows.filter(row => row?.[key] != null && row[key] !== "").map(row => [String(row[key]), { value: String(row[key]), label: String(key === "batchId" ? row.batchCode || row.batchName || row[key] : key === "houseId" ? row.houseName || row.houseCode || row[key] : row[key]) }])).values()];
  const active = [
    filters.query && ["Search", "query"], filters.from && ["From " + filters.from, "from"], filters.to && ["To " + filters.to, "to"],
    filters.batch && ["Batch: " + (options(fields.batchKey).find(x => x.value === filters.batch)?.label || filters.batch), "batch"],
    filters.house && ["House: " + (options(fields.houseKey).find(x => x.value === filters.house)?.label || filters.house), "house"],
    filters.status && ["Status: " + filters.status, "status"],
  ].filter(Boolean);
  return <div className="table-filters" aria-label="Filter records">
    <div className="table-filter-controls">
      <label><span>Search records</span><input type="search" value={filters.query} onChange={e => update("query", e.target.value)} placeholder="Search this list…"/></label>
      {fields.dateKey && <><label><span>From</span><input type="date" value={filters.from} max={filters.to || undefined} onChange={e => update("from", e.target.value)}/></label><label><span>To</span><input type="date" value={filters.to} min={filters.from || undefined} onChange={e => update("to", e.target.value)}/></label></>}
      {fields.batchKey && <label><span>Batch</span><select value={filters.batch} onChange={e => update("batch", e.target.value)}><option value="">All batches</option>{options(fields.batchKey).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>}
      {fields.houseKey && <label><span>House</span><select value={filters.house} onChange={e => update("house", e.target.value)}><option value="">All houses</option>{options(fields.houseKey).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>}
      {fields.statusKey && <label><span>Status</span><select value={filters.status} onChange={e => update("status", e.target.value)}><option value="">All statuses</option>{options(fields.statusKey).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>}
      {active.length > 0 && <button type="button" className="secondary-button" onClick={clear}>Clear all</button>}
    </div>
    {active.length > 0 && <div className="table-filter-chips" aria-live="polite">{active.map(([label, key]) => <FilterChip key={key} label={label} onRemove={() => update(key, "")}/>)}</div>}
    <p className="table-filter-count" aria-live="polite">Showing {filteredRows.length} of {rows.length} loaded records. Filters combine together.</p>
    {onRowsChange(filteredRows)}
  </div>;
}
