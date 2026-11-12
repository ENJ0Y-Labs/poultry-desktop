export const NAVIGATION_SHORTCUTS = Object.freeze({
  "1": "dashboard", "2": "flocks", "3": "daily-work", "4": "feed-stock",
  "5": "health", "6": "production", "7": "sales-money", "8": "reports",
  "9": "import-export", "0": "settings",
});

export function isTypingTarget(target) {
  if (!target || typeof target.closest !== "function") return false;
  return Boolean(target.closest('input, textarea, select, [contenteditable=""], [contenteditable="true"], [role="textbox"]'));
}

export function registerGlobalShortcuts({ navigate, openQuickActions, focusPageSearch }) {
  function onKeyDown(event) {
    if (event.defaultPrevented || event.isComposing || event.key === "Process" || isTypingTarget(event.target)) return;
    if (!event.ctrlKey || event.altKey || event.shiftKey || event.metaKey) return;
    const key = event.key.toLowerCase();
    if (NAVIGATION_SHORTCUTS[event.key]) {
      event.preventDefault();
      navigate?.(NAVIGATION_SHORTCUTS[event.key]);
      return;
    }
    if (key === "k") {
      event.preventDefault();
      openQuickActions?.();
      return;
    }
    if (key === "f" && focusPageSearch?.() !== false) event.preventDefault();
  }
  window.addEventListener("keydown", onKeyDown);
  return () => window.removeEventListener("keydown", onKeyDown);
}
