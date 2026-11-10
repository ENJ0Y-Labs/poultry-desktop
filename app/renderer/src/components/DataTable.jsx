import { useContext } from "react";
import { FarmManagerContext } from "./FarmManagerContext.jsx";
import EmptyState from "./EmptyState.jsx";

export default function DataTable({
  caption,
  columns,
  rows = [],
  emptyMessage = "No records yet.",
  state,
  loading = false,
  error = null,
  filtered = false,
  notApplicable = false,
  onRetry,
  onClearFilters,
  onClearSearch,
  emptyAction,
  emptyDescription,
}) {
  const context = useContext(FarmManagerContext);
  const retrievalError = error || context?.dataLoadError;
  const isLoading = loading || Boolean(context?.loading);
  const retry = onRetry || context?.retryData;
  const hasRows = rows.length > 0;
  const resolvedState = !hasRows && retrievalError ? "error"
    : !hasRows && isLoading ? "loading"
      : !hasRows && notApplicable ? "not-applicable"
        : !hasRows && state === "no-search-results" ? "no-search-results"
          : !hasRows && (filtered || state === "no-filter-results" || state === "no-results") ? "no-filter-results"
            : !hasRows ? (state || "no-records")
              : null;

  return (
    <div className="table-scroll" role="region" aria-label={caption} tabIndex={0}>
      {hasRows && retrievalError && (
        <EmptyState variant="error" description={typeof retrievalError === "string" ? retrievalError : undefined} onRetry={retry} compact />
      )}
      <table className="data-table">
        <caption className="sr-only">{caption}</caption>
        <thead><tr>{columns.map(column => <th scope="col" key={column.key}>{column.label}</th>)}</tr></thead>
        <tbody>
          {resolvedState ? (
            <tr>
              <td colSpan={columns.length} className="data-table__empty-cell">
                <EmptyState
                  variant={resolvedState}
                  description={emptyDescription || (resolvedState === "no-records" ? emptyMessage : undefined)}
                  primaryAction={emptyAction}
                  onRetry={resolvedState === "error" ? retry : undefined}
                  onClearFilters={resolvedState === "no-filter-results" ? onClearFilters : undefined}
                  onClearSearch={resolvedState === "no-search-results" ? onClearSearch : undefined}
                  compact
                />
              </td>
            </tr>
          ) : rows.map((row, index) => (
            <tr key={row.id ?? index}>
              {columns.map(column => <td key={column.key}>{column.render ? column.render(row) : row[column.key] ?? "—"}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
