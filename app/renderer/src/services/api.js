const API_BASE = "http://127.0.0.1:18942/api/v1";

export class ApiError extends Error {
  constructor(code, message, status, details) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(API_BASE + path, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options,
    });
  } catch {
    throw new ApiError(
      "BACKEND_UNAVAILABLE",
      "The local backend is unavailable. Restart the application and try again.",
      503,
    );
  }

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(
      body?.error?.code || "REQUEST_FAILED",
      body?.error?.message || "Request failed.",
      response.status,
      body?.error?.details,
    );
  }
  return body.data;
}

export const farmApi = {
  get: () => request("/farm"),
  create: (data) => request("/farm", { method: "POST", body: JSON.stringify(data) }),
  update: (data) => request("/farm", { method: "PUT", body: JSON.stringify(data) }),
  getSettings: () => request("/farm/settings"),
  updateSettings: (data) => request("/farm/settings", { method: "PUT", body: JSON.stringify(data) }),
  listHouses: () => request("/farm/houses"),
  createHouse: (data) => request("/farm/houses", { method: "POST", body: JSON.stringify(data) }),
  updateHouse: (id, data) => request("/farm/houses/" + id, { method: "PUT", body: JSON.stringify(data) }),
};

export const costApi = {
  get: (batchId, asOf) => request("/batches/" + batchId + "/costs" + (asOf ? "?asOf=" + asOf : "")),
  add: (batchId, data) => request("/batches/" + batchId + "/costs", { method: "POST", body: JSON.stringify(data) }),
};

export const batchApi = {
  list: () => request("/batches"),
  get: (id) => request("/batches/" + id),
  create: (data) => request("/batches", { method: "POST", body: JSON.stringify(data) }),
  markSold: (id) => request("/batches/" + id + "/sold", { method: "POST" }),
  reopen: (id, reason) => request("/batches/" + id + "/reopen", { method: "POST", body: JSON.stringify({ reason }) }),
};

export const feedApi = {
  listTypes: () => request("/feed/types"),
  createType: (data) => request("/feed/types", { method: "POST", body: JSON.stringify(data) }),
  archiveType: (id) => request("/feed/types/" + id + "/archive", { method: "POST" }),
  purchase: (data) => request("/feed/purchases", { method: "POST", body: JSON.stringify(data) }),
  inventory: (asOf) => request("/feed/inventory" + (asOf ? "?asOf=" + asOf : "")),
  use: (batchId, data) => request("/feed/batches/" + batchId + "/usage", { method: "POST", body: JSON.stringify(data) }),
  batchCost: (batchId, asOf) => request("/feed/batches/" + batchId + "/cost" + (asOf ? "?asOf=" + asOf : "")),
};

export const populationApi = {
  mortality: (batchId, data) => request("/batches/" + batchId + "/mortality", { method: "POST", body: JSON.stringify(data) }),
  culling: (batchId, data) => request("/batches/" + batchId + "/culling", { method: "POST", body: JSON.stringify(data) }),
};

export const dailyApi = {
  create: (batchId, data) => request("/batches/" + batchId + "/daily-records", { method: "POST", body: JSON.stringify(data) }),
  get: (batchId, date) => request("/batches/" + batchId + "/daily-records/" + date),
  list: (batchId, from, to) => request("/batches/" + batchId + "/daily-records" + (from || to ? "?from=" + encodeURIComponent(from || "") + "&to=" + encodeURIComponent(to || "") : "")),
};

export const healthApi = {
  add: (batchId, data) => request("/batches/" + batchId + "/health", { method: "POST", body: JSON.stringify(data) }),
  listHealth: (batchId) => request("/batches/" + batchId + "/health"),
  addDrug: (batchId, data) => request("/batches/" + batchId + "/drugs", { method: "POST", body: JSON.stringify(data) }),
  listDrugs: (batchId) => request("/batches/" + batchId + "/drugs"),
  addVaccination: (batchId, data) => request("/batches/" + batchId + "/vaccinations", { method: "POST", body: JSON.stringify(data) }),
  listVaccinations: (batchId) => request("/batches/" + batchId + "/vaccinations"),
};

export const eggApi = {
  addCollection: (batchId, data) => request("/batches/" + batchId + "/eggs/collections", { method: "POST", body: JSON.stringify(data) }),
  listCollections: (batchId, asOf) => request("/batches/" + batchId + "/eggs/collections" + (asOf ? "?asOf=" + asOf : "")),
  addSale: (batchId, data) => request("/batches/" + batchId + "/eggs/sales", { method: "POST", body: JSON.stringify(data) }),
  listSales: (batchId, asOf) => request("/batches/" + batchId + "/eggs/sales" + (asOf ? "?asOf=" + asOf : "")),
  inventory: (batchId, asOf) => request("/batches/" + batchId + "/eggs/inventory" + (asOf ? "?asOf=" + asOf : "")),
};

export const broilerApi = {
  addWeight: (batchId, data) => request("/batches/" + batchId + "/broiler/weights", { method: "POST", body: JSON.stringify(data) }),
  weights: (batchId, asOf) => request("/batches/" + batchId + "/broiler/weights" + (asOf ? "?asOf=" + asOf : "")),
  growth: (batchId, asOf) => request("/batches/" + batchId + "/broiler/growth" + (asOf ? "?asOf=" + asOf : "")),
  addSale: (batchId, data) => request("/batches/" + batchId + "/broiler/sales", { method: "POST", body: JSON.stringify(data) }),
  sales: (batchId, asOf) => request("/batches/" + batchId + "/broiler/sales" + (asOf ? "?asOf=" + asOf : "")),
};

export const pricingApi = {
  settings: () => request("/pricing/settings"),
  updateSettings: (data) => request("/pricing/settings", { method: "PUT", body: JSON.stringify(data) }),
  price: (batchId, quantity, asOf) => request("/pricing/batches/" + batchId + "?quantity=" + encodeURIComponent(quantity) + (asOf ? "&asOf=" + encodeURIComponent(asOf) : "")),
};

export const expenseApi = {
  list: (batchId) => request("/expenses" + (batchId ? "?batchId=" + encodeURIComponent(batchId) : "")),
  create: (data) => request("/expenses", { method: "POST", body: JSON.stringify(data) }),
};

export const inventoryApi = {
  listItems: () => request("/inventory/items"),
  createItem: (data) => request("/inventory/items", { method: "POST", body: JSON.stringify(data) }),
  archiveItem: (id) => request("/inventory/items/" + id + "/archive", { method: "POST" }),
  listMovements: (itemId, asOf) => {
    const query = new URLSearchParams();
    if (itemId) query.set("itemId", itemId);
    if (asOf) query.set("asOf", asOf);
    return request("/inventory/movements" + (query.toString() ? "?" + query.toString() : ""));
  },
  recordMovement: (itemId, data) => request("/inventory/items/" + itemId + "/movements", { method: "POST", body: JSON.stringify(data) }),
};

export const supplierApi = {
  list: () => request("/suppliers"),
  create: (data) => request("/suppliers", { method: "POST", body: JSON.stringify(data) }),
  archive: (id) => request("/suppliers/" + id + "/archive", { method: "POST" }),
};

export const customerApi = {
  list: () => request("/customers"),
  create: (data) => request("/customers", { method: "POST", body: JSON.stringify(data) }),
  update: (id, data) => request("/customers/" + id, { method: "PUT", body: JSON.stringify(data) }),
};

export const salesApi = {
  list: (params = {}) => {
    const query = new URLSearchParams();
    if (params.batchId) query.set("batchId", params.batchId);
    if (params.customerId) query.set("customerId", params.customerId);
    if (params.saleType) query.set("saleType", params.saleType);
    if (params.asOf) query.set("asOf", params.asOf);
    return request("/sales" + (query.toString() ? "?" + query.toString() : ""));
  },
};

export const authApi = {
  setup: (data) => request("/auth/setup", { method: "POST", body: JSON.stringify(data) }),
  login: (data) => request("/auth/login", { method: "POST", body: JSON.stringify(data) }),
  logout: () => request("/auth/logout", { method: "POST" }),
  me: () => request("/auth/me"),
};

export const dashboardApi = {
  farm: (asOf) => request("/dashboard" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
  batch: (id, asOf) => request("/batches/" + id + "/dashboard" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
};

export const attentionApi = {
  list: (asOf) => request("/attention" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
};

async function downloadText(path) {
  const response = await fetch(API_BASE + path, { credentials: "include" });
  if (!response.ok) throw new ApiError("EXPORT_FAILED", "Report export failed.", response.status);
  return response.text();
}

async function downloadBlob(path) {
  const response = await fetch(API_BASE + path, { credentials: "include" });
  if (!response.ok) throw new ApiError("EXPORT_FAILED", "Report export failed.", response.status);
  return response.blob();
}

export const reportApi = {
  farm: (asOf) => request("/reports/farm" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
  batch: (id, asOf) => request("/reports/batches/" + id + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
  farmCsv: (asOf) => downloadText("/reports/farm.csv" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
  batchCsv: (id, asOf) => downloadText("/reports/batches/" + id + ".csv" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
  farmPdf: (asOf) => downloadBlob("/reports/farm.pdf" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
  batchPdf: (id, asOf) => downloadBlob("/reports/batches/" + id + ".pdf" + (asOf ? "?asOf=" + encodeURIComponent(asOf) : "")),
};

export const auditApi = {
  list: (limit = 100) => request("/audit?limit=" + encodeURIComponent(limit)),
};

export const backupApi = {
  create: (directory) => request("/backup", { method: "POST", body: JSON.stringify({ directory }) }),
  validate: (file) => request("/backup/validate", { method: "POST", body: JSON.stringify({ file }) }),
};
