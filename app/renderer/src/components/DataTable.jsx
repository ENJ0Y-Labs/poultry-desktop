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
  const resolvedState = error ? "error"
    : loading ? "loading"
      : notApplicable ? "not-applicable"
        : rows.length === 0 && state === "no-search-results" ? "no-search-results"
          : rows.length === 0 && (filtered || state === "no-filter-results" || state === "no-results") ? "no-filter-results"
          : rows.length === 0 ? (state || "no-records")
            : null;

  return (
    <div className="table-scroll" role="region" aria-label={caption} tabIndex={0}>
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
                  onRetry={onRetry}
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
