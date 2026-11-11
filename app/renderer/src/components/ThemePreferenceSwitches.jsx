import { useEffect, useState } from "react";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ToggleSwitch from "./ToggleSwitch.jsx";

function prefersDarkAppearance() {
  const lightPreference = window.matchMedia?.("(prefers-color-scheme: light)");
  return lightPreference ? !lightPreference.matches : document.documentElement.dataset.theme !== "light";
}

export default function ThemePreferenceSwitches({ value, onChange, className = "" }) {
  const [systemDark, setSystemDark] = useState(prefersDarkAppearance);

  useEffect(() => {
    const lightPreference = window.matchMedia?.("(prefers-color-scheme: light)");
    if (!lightPreference) return undefined;
    const updateSystemAppearance = event => setSystemDark(!event.matches);
    lightPreference.addEventListener?.("change", updateSystemAppearance);
    return () => lightPreference.removeEventListener?.("change", updateSystemAppearance);
  }, []);

  return (
    <div className={`theme-switches ${className}`.trim()}>
      <ToggleSwitch
        label="System"
        ariaLabel="Follow system appearance"
        checked={value === "auto"}
        onChange={enabled => onChange(enabled ? "auto" : systemDark ? "dark" : "light")}
      />
      <ToggleSwitch
        label="Dark"
        ariaLabel="Dark appearance"
        checked={value === "dark" || (value === "auto" && systemDark)}
        disabled={value === "auto"}
        title={value === "auto" ? "The system appearance controls dark mode while enabled." : undefined}
        onChange={enabled => onChange(enabled ? "dark" : "light")}
      />
    </div>
  );
}
