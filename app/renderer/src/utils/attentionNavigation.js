const alertRoutes = {
  MISSING_DAILY_RECORD: {
    page: "daily-work",
    tab: "daily-record",
    required: ["batchId", "date"],
  },
  HIGH_MORTALITY: {
    page: "flocks",
    tab: "batch-dashboard",
    required: ["batchId"],
  },
  VACCINATION_DUE: {
    page: "health",
    tab: "vaccination",
    required: ["batchId"],
  },
  UNUSUAL_PRODUCTION_DROP: {
    page: "production",
    tab: "eggs",
    required: ["batchId"],
  },
  BATCH_NEARING_SALE: {
    page: "production",
    tab: "broiler",
    required: ["batchId"],
  },
  LOW_FEED_STOCK: {
    page: "feed-stock",
    tab: "purchases",
    required: ["feedTypeId"],
  },
  LOW_INVENTORY: {
    page: "feed-stock",
    tab: "inventory",
    required: ["itemId"],
  },
  INSUFFICIENT_INVENTORY: {
    page: "feed-stock",
    tab: "inventory",
    required: ["itemId"],
  },
  BACKUP_OVERDUE: {
    page: "settings",
    tab: "backup",
    required: [],
  },
};

export function attentionTarget(alert) {
  if (!alert || typeof alert !== "object") return null;
  const route = alertRoutes[alert.type];
  if (!route) return null;
  if (route.required.some(key => {
    const value = alert[key];
    return value == null || String(value).trim() === "";
  })) return null;

  const context = [];
  for (const key of ["batchId", "feedTypeId", "itemId", "date"]) {
    const value = alert[key];
    if (value != null && String(value).trim() !== "") {
      context.push(`${key}=${encodeURIComponent(String(value))}`);
    }
  }
  return `#${route.page}/${route.tab}${context.length ? `/${context.join("/")}` : ""}`;
}

export function parseAttentionContext(hash) {
  const segments = String(hash || "").replace(/^#\/?/, "").split("/");
  const context = {};
  for (const segment of segments.slice(2)) {
    const separator = segment.indexOf("=");
    if (separator <= 0) continue;
    const key = segment.slice(0, separator);
    if (!["batchId", "feedTypeId", "itemId", "date"].includes(key)) continue;
    try {
      context[key] = decodeURIComponent(segment.slice(separator + 1));
    } catch {
      // Ignore malformed context rather than breaking navigation.
    }
  }
  return context;
}
