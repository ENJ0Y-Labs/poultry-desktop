import { createElement, useContext, useEffect, useMemo, useRef, useState } from "react";
import { FarmManagerContext } from "./FarmManagerContext.jsx";
import EmptyState from "./EmptyState.jsx";
import TableFilters, { EMPTY } from "./TableFilters.jsx";
import RowActionsMenu from "./RowActionsMenu.jsx";
import { filterTableRows } from "../utils/tableFilters.js";
import { sortTableRows, paginateTableRows } from "../utils/tableSorting.js";

const DATE_KEYS = ["recordDate", "occurredDate", "saleDate", "purchaseDate", "date", "effectiveDate", "vaccinationDate", "administeredDate", "createdAt"];
const BATCH_KEYS = ["batchId", "batchCode"];
const HOUSE_KEYS = ["houseId", "houseName", "houseCode"];
const STATUS_KEYS = ["status", "state"];
const PAGE_SIZES = [10, 25, 50, 100];
function readFilters(key) {
  try { return { ...EMPTY, ...JSON.parse(window.localStorage.getItem(key) || "{}") }; }
  catch { return EMPTY; }
}
export default function DataTable({
  caption, columns, rows = [], emptyMessage = "No records yet.", state, loading = false, error = null,
  filtered = false, notApplicable = false, onRetry, onClearFilters, onClearSearch, emptyAction, emptyDescription,
  enableFilters = true, rowActions,
}) {
  const context = useContext(FarmManagerContext);
  const retrievalError = error || context?.dataLoadError;
  const isLoading = loading || Boolean(context?.loading);
  const retry = onRetry || context?.retryData;
  const storageKey = `pomas:table-filters:${caption}`;
  const [filters, setFilters] = useState(() => readFilters(storageKey));
  const [sort, setSort] = useState({ key: null, direction: "asc" });
  const [pageSize, setPageSize] = useState(10);
  const [page, setPage] = useState(0);
  const fields = useMemo(() => ({
    dateKey: DATE_KEYS.find(key => rows.some(row => row?.[key] != null)),
    batchKey: BATCH_KEYS.find(key => rows.some(row => row?.[key] != null)),
    houseKey: HOUSE_KEYS.find(key => rows.some(row => row?.[key] != null)),
    statusKey: STATUS_KEYS.find(key => rows.some(row => row?.[key] != null)),
  }), [rows]);
  const visibleRows = useMemo(() => enableFilters ? filterTableRows(rows, filters, fields) : rows, [enableFilters, rows, filters, fields]);
  const sortedRows = useMemo(() => sortTableRows(visibleRows, sort, columns), [visibleRows, sort, columns]);
  const pageCount = Math.ceil(sortedRows.length / pageSize);
  const safePage = Math.min(page, Math.max(0, pageCount - 1));
  const pageRows = useMemo(() => paginateTableRows(sortedRows, safePage, pageSize), [sortedRows, safePage, pageSize]);

  const paginationResetKey = JSON.stringify({ filters, pageSize });
  const previousResetKey = useRef(paginationResetKey);
  useEffect(() => {
    if (previousResetKey.current !== paginationResetKey) {
      previousResetKey.current = paginationResetKey;
      setPage(0);
      return;
    }
    if (page !== safePage) setPage(safePage);
  }, [paginationResetKey, page, safePage]);

  const hasRows = visibleRows.length > 0;
  const hasActiveFilters = Object.values(filters).some(Boolean);
  const resolvedState = !hasRows && retrievalError ? "error"
    : !hasRows && isLoading ? "loading"
      : !hasRows && notApplicable ? "not-applicable"
        : !hasRows && enableFilters && filters.query ? "no-search-results"
          : !hasRows && (filtered || hasActiveFilters || state === "no-filter-results" || state === "no-results") ? "no-filter-results"
            : !hasRows ? (state || "no-records")
              : null;

  function toggleSort(column) {
    if (column.sortable === false) return;
    setSort(current => current.key === column.key
      ? { key: column.key, direction: current.direction === "asc" ? "desc" : "asc" }
      : { key: column.key, direction: "asc" });
    setPage(0);
  }

  return (
    <div className="data-table-wrap">
      {enableFilters && rows.length > 0 && createElement(TableFilters, { rows, fields, filters, setFilters, filteredCount: visibleRows.length, storageKey })}
      <div className="table-scroll" role="region" aria-label={caption} tabIndex={0}>
        {hasRows && retrievalError && createElement(EmptyState, { variant: "error", description: typeof retrievalError === "string" ? retrievalError : undefined, onRetry: retry, compact: true })}
        <table className="data-table">
          <caption className="sr-only">{caption}</caption>
          <thead><tr>{columns.map(column => {
            const active = sort.key === column.key;
            const direction = active ? sort.direction : null;
            return <th scope="col" key={column.key} aria-sort={active ? (direction === "asc" ? "ascending" : "descending") : "none"}>
              {column.sortable === false ? column.label : <button type="button" className="data-table__sort" onClick={() => toggleSort(column)} aria-label={`Sort by ${column.label}${active ? direction === "asc" ? ", currently ascending" : ", currently descending" : ""}`}>
                <span>{column.label}</span><span className="data-table__sort-indicator" aria-hidden="true">{active ? direction === "asc" ? "↑" : "↓" : "↕"}</span>
              </button>}
            </th>;
          })}{rowActions && <th scope="col"><span className="sr-only">Actions</span></th>}</tr></thead>
          <tbody>
            {resolvedState ? <tr><td colSpan={columns.length + (rowActions ? 1 : 0)} className="data-table__empty-cell">
              {createElement(EmptyState, {
                variant: resolvedState,
                description: emptyDescription || (resolvedState === "error" ? (typeof retrievalError === "string" ? retrievalError : retrievalError?.message) : resolvedState === "no-records" ? emptyMessage : undefined),
                primaryAction: emptyAction, onRetry: resolvedState === "error" ? retry : undefined,
                onClearFilters: resolvedState === "no-filter-results" ? () => { setFilters(EMPTY); setPage(0); try { window.localStorage.removeItem(storageKey); } catch { /* Storage is optional. */ } onClearFilters?.(); } : undefined,
                onClearSearch: resolvedState === "no-search-results" ? () => { setFilters(EMPTY); setPage(0); try { window.localStorage.removeItem(storageKey); } catch { /* Storage is optional. */ } onClearSearch?.(); } : undefined,
                compact: true,
              })}</td></tr>
              : pageRows.map((row, index) => <tr key={row.id ?? row.uuid ?? row.code ?? row.batchCode ?? index}>
                {columns.map(column => <td key={column.key}>{column.render ? column.render(row) : row[column.key] ?? "—"}</td>)}
                {rowActions && <td className="data-table__actions-cell"><RowActionsMenu row={row} actions={rowActions(row)} label={`Actions for ${row.name || row.code || row.id || "record"}`} /></td>}
              </tr>)}
          </tbody>
        </table>
      </div>
      {hasRows && <div className="data-table__pagination" aria-label={`${caption} pagination`}>
        <span className="data-table__page-summary">Showing {safePage * pageSize + 1}–{Math.min((safePage + 1) * pageSize, sortedRows.length)} of {sortedRows.length}</span>
        <label className="data-table__page-size">Rows per page
          <select value={pageSize} onChange={event => setPageSize(Number(event.target.value))} aria-label="Rows per page">
            {PAGE_SIZES.map(size => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <button type="button" className="secondary-button" onClick={() => setPage(0)} disabled={safePage === 0} aria-label="First page">«</button>
        <button type="button" className="secondary-button" onClick={() => setPage(value => Math.max(0, value - 1))} disabled={safePage === 0}>Previous</button>
        <span className="data-table__page-number">Page {safePage + 1} of {Math.max(1, pageCount)}</span>
        <button type="button" className="secondary-button" onClick={() => setPage(value => Math.min(pageCount - 1, value + 1))} disabled={safePage >= pageCount - 1}>Next</button>
        <button type="button" className="secondary-button" onClick={() => setPage(pageCount - 1)} disabled={safePage >= pageCount - 1} aria-label="Last page">»</button>
      </div>}
    </div>
  );
}
