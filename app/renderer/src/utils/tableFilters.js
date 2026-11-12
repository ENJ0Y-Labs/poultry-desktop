function dateValue(value) {
  if (!value) return null;
  const match = String(value).match(/^\d{4}-\d{2}-\d{2}/);
  return match ? match[0] : null;
}
export function filterTableRows(rows, filters, fields = {}) {
  const { dateKey, batchKey, houseKey, statusKey } = fields;
  const query = filters.query.trim().toLocaleLowerCase();
  return rows.filter(row => {
    if (query && !Object.values(row || {}).some(value => value != null && String(value).toLocaleLowerCase().includes(query))) return false;
    if (filters.batch && String(row?.[batchKey]) !== filters.batch) return false;
    if (filters.house && String(row?.[houseKey]) !== filters.house) return false;
    if (filters.status && String(row?.[statusKey]) !== filters.status) return false;
    const date = dateValue(row?.[dateKey]);
    if (filters.from && (!date || date < filters.from)) return false;
    if (filters.to && (!date || date > filters.to)) return false;
    return true;
  });
}
