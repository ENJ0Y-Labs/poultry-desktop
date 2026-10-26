const ACTION_LABELS = {
  CREATE: "Created",
  UPDATE: "Updated",
  DELETE: "Deleted",
  VOID: "Voided",
  ARCHIVE: "Archived",
  RESTORE: "Restored",
  LOGIN: "Signed in",
  LOGOUT: "Signed out",
  SETUP: "Set up",
};

const ENTITY_LABELS = {
  FARM: "Farm",
  FARM_SETTINGS: "Farm settings",
  HOUSE: "House / pen",
  BATCH: "Batch",
  FLOCK: "Flock",
  LAYER_BATCH: "Layer batch",
  BROILER_BATCH: "Broiler batch",
  FEED_TYPE: "Feed type",
  FEED_PURCHASE: "Feed purchase",
  FEED_USAGE: "Feed usage",
  DAILY_RECORD: "Daily record",
  MORTALITY: "Mortality record",
  CULLING: "Culling record",
  HEALTH_RECORD: "Health record",
  DRUG_RECORD: "Drug record",
  VACCINATION_RECORD: "Vaccination record",
  EGG_COLLECTION: "Egg collection",
  EGG_SALE: "Egg sale",
  BIRD_SALE: "Bird sale",
  SALE: "Sale",
  EXPENSE: "Expense",
  CUSTOMER: "Customer",
  SUPPLIER: "Supplier",
  INVENTORY_ITEM: "Stock item",
  INVENTORY_MOVEMENT: "Stock movement",
  USER: "User account",
};

function readableUnknown(value, fallback) {
  if (typeof value !== "string" || !value.trim()) return fallback;
  const words = value.trim()
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_.-]+/g, " ")
    .replace(/\s+/g, " ");
  return words.toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
}

export function formatAuditTimestamp(value, timeZone = "Africa/Lagos") {
  if (typeof value !== "string" || !value.trim()) return "—";

  // JavaScript Date supports milliseconds; trim extra ISO fractional digits only
  // for display parsing. The original audit value remains untouched.
  const normalized = value.trim().replace(/\.(\d{3})\d+(?=(?:Z|[+-]\d{2}:?\d{2})?$)/i, ".$1");
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return "Unknown time";

  const options = {
    timeZone: typeof timeZone === "string" && timeZone.trim() ? timeZone : "Africa/Lagos",
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  };

  try {
    const parts = new Intl.DateTimeFormat("en-GB", options).formatToParts(date);
    const part = type => parts.find(item => item.type === type)?.value;
    return `${part("day")} ${part("month")} ${part("year")}, ${part("hour")}:${part("minute")}:${part("second")}`;
  } catch {
    // Invalid farm timezone configuration must not break the dashboard.
    const parts = new Intl.DateTimeFormat("en-GB", { ...options, timeZone: "UTC" }).formatToParts(date);
    const part = type => parts.find(item => item.type === type)?.value;
    return `${part("day")} ${part("month")} ${part("year")}, ${part("hour")}:${part("minute")}:${part("second")} UTC`;
  }
}

export function formatAuditAction(value) {
  if (typeof value !== "string" || !value.trim()) return "Unknown action";
  const normalized = value.trim().toUpperCase();
  return ACTION_LABELS[normalized] || readableUnknown(value, "Unknown action");
}

export function formatAuditEntity(value) {
  if (typeof value !== "string" || !value.trim()) return "Unknown item";
  const normalized = value.trim().toUpperCase();
  return ENTITY_LABELS[normalized] || readableUnknown(value, "Unknown item");
}
