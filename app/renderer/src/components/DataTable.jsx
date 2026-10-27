export default function DataTable({ caption, columns, rows, emptyMessage = "No records yet." }) {
  return (
    <div className="table-scroll" role="region" aria-label={caption} tabIndex={0}>
      <table className="data-table">
        <caption className="sr-only">{caption}</caption>
        <thead><tr>{columns.map(column => <th scope="col" key={column.key}>{column.label}</th>)}</tr></thead>
        <tbody>
          {rows.length > 0 ? rows.map((row, index) => (
            <tr key={row.id ?? index}>
              {columns.map(column => <td key={column.key}>{column.render ? column.render(row) : row[column.key] ?? "—"}</td>)}
            </tr>
          )) : <tr><td colSpan={columns.length} className="empty">{emptyMessage}</td></tr>}
        </tbody>
      </table>
    </div>
  );
}
