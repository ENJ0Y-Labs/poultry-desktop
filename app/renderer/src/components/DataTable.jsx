import { createElement, useContext, useMemo, useState } from "react";
import { FarmManagerContext } from "./FarmManagerContext.jsx";
import EmptyState from "./EmptyState.jsx";
import TableFilters, { EMPTY } from "./TableFilters.jsx";
import { filterTableRows } from "../utils/tableFilters.js";

const DATE_KEYS = ["recordDate", "occurredDate", "saleDate", "purchaseDate", "date", "effectiveDate", "vaccinationDate", "administeredDate", "createdAt"];
const BATCH_KEYS = ["batchId", "batchCode"];
const HOUSE_KEYS = ["houseId", "houseName", "houseCode"];
const STATUS_KEYS = ["status", "state"];
function readFilters(key) {
  try { return { ...EMPTY, ...JSON.parse(window.localStorage.getItem(key) || "{}") }; }
  catch { return EMPTY; }
}
export default function DataTable({
  caption, columns, rows = [], emptyMessage = "No records yet.", state, loading = false, error = null,
  filtered = false, notApplicable = false, onRetry, onClearFilters, onClearSearch, emptyAction, emptyDescription,
  enableFilters = true,
}) {
  const context = useContext(FarmManagerContext);
  const retrievalError = error || context?.dataLoadError;
  const isLoading = loading || Boolean(context?.loading);
  const retry = onRetry || context?.retryData;
  const storageKey = `pomas:table-filters:${caption}`;
  const [filters, setFilters] = useState(() => readFilters(storageKey));
  const fields = useMemo(() => ({
    dateKey: DATE_KEYS.find(key => rows.some(row => row?.[key] != null)),
    batchKey: BATCH_KEYS.find(key => rows.some(row => row?.[key] != null)),
    houseKey: HOUSE_KEYS.find(key => rows.some(row => row?.[key] != null)),
    statusKey: STATUS_KEYS.find(key => rows.some(row => row?.[key] != null)),
  }), [rows]);
  const visibleRows = useMemo(() => enableFilters ? filterTableRows(rows, filters, fields) : rows, [enableFilters, rows, filters, fields]);
  const hasRows = visibleRows.length > 0;
  const hasActiveFilters = Object.values(filters).some(Boolean);
  const resolvedState = !hasRows && retrievalError ? "error"
    : !hasRows && isLoading ? "loading"
      : !hasRows && notApplicable ? "not-applicable"
        : !hasRows && enableFilters && filters.query ? "no-search-results"
          : !hasRows && (filtered || hasActiveFilters || state === "no-filter-results" || state === "no-results") ? "no-filter-results"
            : !hasRows ? (state || "no-records")
              : null;
  return (
    <div className="data-table-wrap">
      {enableFilters && rows.length > 0 && <TableFilters rows={rows} fields={fields} filters={filters} setFilters={setFilters} filteredCount={visibleRows.length} storageKey={storageKey}/>}
      <div className="table-scroll" role="region" aria-label={caption} tabIndex={0}>
        {hasRows && retrievalError && createElement(EmptyState, { variant: "error", description: typeof retrievalError === "string" ? retrievalError : undefined, onRetry: retry, compact: true })}
        <table className="data-table">
          <caption className="sr-only">{caption}</caption>
          <thead><tr>{columns.map(column => <th scope="col" key={column.key}>{column.label}</th>)}</tr></thead>
          <tbody>
            {resolvedState ? <tr><td colSpan={columns.length} className="data-table__empty-cell">
              {createElement(EmptyState, {
                variant: resolvedState,
                description: emptyDescription || (resolvedState === "error" ? (typeof retrievalError === "string" ? retrievalError : retrievalError?.message) : resolvedState === "no-records" ? emptyMessage : undefined),
                primaryAction: emptyAction, onRetry: resolvedState === "error" ? retry : undefined,
                onClearFilters: resolvedState === "no-filter-results" ? () => { setFilters(EMPTY); try { window.localStorage.removeItem(storageKey); } catch { /* Storage is optional. */ } onClearFilters?.(); } : undefined,
                onClearSearch: resolvedState === "no-search-results" ? () => { setFilters(EMPTY); try { window.localStorage.removeItem(storageKey); } catch { /* Storage is optional. */ } onClearSearch?.(); } : undefined,
                compact: true,
              })}</td></tr>
              : visibleRows.map((row, index) => <tr key={row.id ?? index}>{columns.map(column => <td key={column.key}>{column.render ? column.render(row) : row[column.key] ?? "—"}</td>)}</tr>)}
          </tbody>
        </table>
      </div>
    </div>
  );
}
