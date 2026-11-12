const DATE_KEYS = ["recordDate", "occurredDate", "saleDate", "purchaseDate", "date", "effectiveDate", "vaccinationDate", "administeredDate", "createdAt"];
const BATCH_KEYS = ["batchId", "batchCode"];
const HOUSE_KEYS = ["houseId", "houseName", "houseCode"];
const STATUS_KEYS = ["status", "state"];

function firstKey(rows, keys) {
  return keys.find(key => rows.some(row => row?.[key] != null && row[key] !== ""));
}
function labelFor(row, key) {
  if (key === "batchId") return row.batchCode || row.batchName || row[key];
  if (key === "houseId") return row.houseName || row.houseCode || row[key];
  return row[key];
}
function uniqueOptions(rows, key) {
  return [...new Map(rows.filter(row => row?.[key] != null && row[key] !== "")
    .map(row => [String(row[key]), { value: String(row[key]), label: String(labelFor(row, key)) }])).values()]
    .sort((a, b) => a.label.localeCompare(b.label));
}
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
