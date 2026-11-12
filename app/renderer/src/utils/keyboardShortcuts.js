export const NAVIGATION_SHORTCUTS = Object.freeze({
  "1": "dashboard", "2": "flocks", "3": "daily-work", "4": "feed-stock",
  "5": "health", "6": "production", "7": "sales-money", "8": "reports",
  "9": "import-export", "0": "settings",
});

export function isTypingTarget(target) {
  if (!target || typeof target.closest !== "function") return false;
  if (target.closest('input, textarea, select, [role="textbox"]')) return true;

  // contenteditable is inherited. Check the nearest explicit value so a
  // contenteditable="false" child is not treated as editable just because
  // one of its ancestors is an editor.
  let element = target;
  while (element) {
    const contentEditable = element.getAttribute?.("contenteditable");
    if (contentEditable !== null && contentEditable !== undefined) {
      return contentEditable.toLowerCase() !== "false";
    }
    if (element.isContentEditable) return true;
    element = element.parentElement;
  }
  return false;
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
