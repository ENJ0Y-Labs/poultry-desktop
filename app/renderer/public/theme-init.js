(() => {
  const root = document.documentElement;
  let saved = null;
  try {
    saved = localStorage.getItem("pomas:theme") || localStorage.getItem("grantino:theme");
  } catch {
    // Auto remains available when browser storage is blocked.
  }

  const preference = ["auto", "light", "dark"].includes(saved) ? saved : "auto";
  let systemPrefersLight = false;
  try {
    systemPrefersLight = window.matchMedia?.("(prefers-color-scheme: light)").matches === true;
  } catch {
    // Use the safe Dark fallback if the system preference cannot be read.
  }

  root.dataset.themePreference = preference;
  root.dataset.theme = preference === "auto" ? (systemPrefersLight ? "light" : "dark") : preference;
})();
