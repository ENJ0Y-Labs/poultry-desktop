function stableId(row) {
  const value = row?.id ?? row?.uuid ?? row?.code ?? row?.batchCode;
  return value == null ? "" : String(value);
}
function compareValues(left, right) {
  if (left == null && right == null) return 0;
  if (left == null) return 1;
  if (right == null) return -1;
  if (typeof left === "number" && typeof right === "number") return left - right;
  const a = String(left), b = String(right);
  if (/^\d{4}-\d{2}-\d{2}/.test(a) && /^\d{4}-\d{2}-\d{2}/.test(b)) return a.localeCompare(b);
  const na = Number(a), nb = Number(b);
  if (a.trim() && b.trim() && Number.isFinite(na) && Number.isFinite(nb)) return na - nb;
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: "base" });
}
export function sortTableRows(rows, sort, columns = []) {
  if (!sort?.key) return [...rows];
  const column = columns.find(item => item.key === sort.key);
  if (!column || column.sortable === false) return [...rows];
  const direction = sort.direction === "desc" ? -1 : 1;
  return rows.map((row, index) => ({ row, index })).sort((a, b) => {
    const av = column.sortValue ? column.sortValue(a.row) : a.row?.[sort.key];
    const bv = column.sortValue ? column.sortValue(b.row) : b.row?.[sort.key];
    const primary = compareValues(av, bv);
    if (primary) return primary * direction;
    return stableId(a.row).localeCompare(stableId(b.row), undefined, { numeric: true }) || a.index - b.index;
  }).map(item => item.row);
}
export function paginateTableRows(rows, page, pageSize) {
  const size = [10, 25, 50, 100].includes(pageSize) ? pageSize : 10;
  const count = Math.ceil(rows.length / size);
  const safePage = Math.min(Math.max(0, Number.isInteger(page) ? page : 0), Math.max(0, count - 1));
  return rows.slice(safePage * size, (safePage + 1) * size);
}
