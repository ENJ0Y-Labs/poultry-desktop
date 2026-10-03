export function todayLocalDate() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

const LAST_BATCH_KEY = "grantino:lastBatchId";

export function getLastBatchId() {
  try {
    return localStorage.getItem(LAST_BATCH_KEY) || "";
  } catch {
    return "";
  }
}

export function rememberLastBatchId(batchId) {
  if (!batchId) return;
  try {
    localStorage.setItem(LAST_BATCH_KEY, batchId);
  } catch {
    // Storage is a convenience, not a reason to block farm work.
  }
}
