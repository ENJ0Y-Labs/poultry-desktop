export const DATE_RANGE_PRESETS = Object.freeze([
  { value: "TODAY", label: "Today" },
  { value: "YESTERDAY", label: "Yesterday" },
  { value: "LAST_7_DAYS", label: "Last 7 days" },
  { value: "LAST_30_DAYS", label: "Last 30 days" },
  { value: "LAST_90_DAYS", label: "Last 90 days" },
  { value: "THIS_MONTH", label: "This month" },
  { value: "LAST_MONTH", label: "Last month" },
  { value: "THIS_YEAR", label: "This year" },
  { value: "CUSTOM", label: "Custom range" },
]);

function dateInTimeZone(date, timeZone) {
  try {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: timeZone || "UTC",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(date);
    const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
    return `${values.year}-${values.month}-${values.day}`;
  } catch {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: "UTC",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(date);
    const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
    return `${values.year}-${values.month}-${values.day}`;
  }
}

function parseDate(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const date = new Date(`${value}T00:00:00.000Z`);
  return Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value ? null : date;
}

function toDateString(date) {
  return date.toISOString().slice(0, 10);
}

function addDays(value, amount) {
  const date = parseDate(value);
  if (!date) return "";
  date.setUTCDate(date.getUTCDate() + amount);
  return toDateString(date);
}

export function createDateRange(preset = "LAST_30_DAYS", timeZone = "Africa/Lagos", now = new Date()) {
  const today = dateInTimeZone(now, timeZone);
  const todayDate = parseDate(today);
  if (!todayDate) return { preset, from: today, to: today };

  const year = todayDate.getUTCFullYear();
  const month = todayDate.getUTCMonth();
  const firstOfMonth = toDateString(new Date(Date.UTC(year, month, 1)));
  let from = today;
  let to = today;

  switch (preset) {
    case "YESTERDAY":
      from = addDays(today, -1);
      to = from;
      break;
    case "LAST_7_DAYS":
      from = addDays(today, -6);
      break;
    case "LAST_30_DAYS":
      from = addDays(today, -29);
      break;
    case "LAST_90_DAYS":
      from = addDays(today, -89);
      break;
    case "THIS_MONTH":
      from = firstOfMonth;
      break;
    case "LAST_MONTH":
      to = addDays(firstOfMonth, -1);
      from = toDateString(new Date(Date.UTC(
        Number(to.slice(0, 4)),
        Number(to.slice(5, 7)) - 1,
        1,
      )));
      break;
    case "THIS_YEAR":
      from = `${year}-01-01`;
      break;
    case "CUSTOM":
      return { preset, from: today, to: today };
    case "TODAY":
    default:
      return { preset: "TODAY", from: today, to: today };
  }

  return { preset, from, to };
}

export function isValidDateRange(range) {
  if (!range) return false;
  const from = parseDate(range.from);
  const to = parseDate(range.to);
  return Boolean(from && to && from.getTime() <= to.getTime());
}

export function formatDateRange(range) {
  if (!isValidDateRange(range)) return "Invalid date range";
  const formatter = new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
  const from = formatter.format(parseDate(range.from));
  const to = formatter.format(parseDate(range.to));
  return range.from === range.to ? from : `${from} – ${to}`;
}
