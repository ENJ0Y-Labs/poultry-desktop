(() => {
  const root = document.documentElement;
  try {
    const saved = localStorage.getItem("pomas:theme") || localStorage.getItem("grantino:theme");
    const preference = ["auto", "light", "dark"].includes(saved) ? saved : "auto";
    const systemPrefersLight = window.matchMedia?.("(prefers-color-scheme: light)").matches === true;
    root.dataset.themePreference = preference;
    root.dataset.theme = preference === "auto" ? (systemPrefersLight ? "light" : "dark") : preference;
  } catch {
    root.dataset.themePreference = "auto";
    root.dataset.theme = "dark";
  }
})();
