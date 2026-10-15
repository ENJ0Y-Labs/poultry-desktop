const THEME_KEY = "grantino:theme";
const THEMES = new Set(["auto", "light", "dark"]);
let mediaQuery;

export function getThemePreference() {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    return THEMES.has(saved) ? saved : "auto";
  } catch {
    return "auto";
  }
}

function effectiveTheme(preference) {
  if (preference === "light" || preference === "dark") return preference;
  return window.matchMedia?.("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

export function applyTheme(preference = getThemePreference()) {
  const safePreference = THEMES.has(preference) ? preference : "auto";
  document.documentElement.dataset.theme = effectiveTheme(safePreference);
  document.documentElement.dataset.themePreference = safePreference;
  if (safePreference === "auto") {
    if (mediaQuery) mediaQuery.removeEventListener?.("change", onSystemThemeChange);
    mediaQuery = window.matchMedia?.("(prefers-color-scheme: light)");
    mediaQuery?.addEventListener?.("change", onSystemThemeChange);
  } else if (mediaQuery) {
    mediaQuery.removeEventListener?.("change", onSystemThemeChange);
    mediaQuery = undefined;
  }
  try { window.poultryDesktop?.setTheme(safePreference); } catch { /* UI theme still works without the desktop bridge. */ }
  return safePreference;
}

function onSystemThemeChange() {
  if (getThemePreference() === "auto") document.documentElement.dataset.theme = effectiveTheme("auto");
}

export function setThemePreference(preference) {
  if (!THEMES.has(preference)) throw new TypeError("Theme must be auto, light, or dark.");
  try { localStorage.setItem(THEME_KEY, preference); } catch { /* Keep the current session usable if storage is unavailable. */ }
  return applyTheme(preference);
}

export function initializeTheme() {
  return applyTheme(getThemePreference());
}
