const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:18942/api/v1";

async function request(path, options = {}) {
  const response = await fetch(API_BASE + path, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body?.error?.message || "Request failed.");
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
  add: (batchId, data) => request("/batches/" + batchId + "/costs", {
    method: "POST",
    body: JSON.stringify(data),
  }),
};

export const batchApi = {
  list: () => request("/batches"),
  get: (id) => request("/batches/" + id),
  create: (data) => request("/batches", { method: "POST", body: JSON.stringify(data) }),
  markSold: (id) => request("/batches/" + id + "/sold", { method: "POST" }),
  reopen: (id, reason) => request("/batches/" + id + "/reopen", {
    method: "POST",
    body: JSON.stringify({ reason }),
  }),
};
