import { useEffect, useMemo, useRef, useState } from "react";
// eslint-disable-next-line no-unused-vars
import { NavigationContext } from "./NavigationContext.jsx";
import { getThemePreference, setThemePreference } from "../utils/theme.js";
import { connectionApi } from "../services/api.js";
import pomasLogo from "../assets/pomas-logo.png";

const groups = [
  { label: "WORKSPACE", items: [
    { label: "Dashboard", target: "dashboard", icon: "dashboard", shortcut: "Ctrl+1" },
    { label: "Flocks", target: "flocks", icon: "batches", shortcut: "Ctrl+2" },
    { label: "Daily Work", target: "daily-work", icon: "records", shortcut: "Ctrl+3" },
    { label: "Feed & Stock", target: "feed-stock", icon: "feed", shortcut: "Ctrl+4" },
    { label: "Health", target: "health", icon: "health", shortcut: "Ctrl+5" },
    { label: "Production", target: "production", icon: "eggs", shortcut: "Ctrl+6" },
    { label: "Sales & Money", target: "sales-money", icon: "sales", shortcut: "Ctrl+7" },
    { label: "Reports", target: "reports", icon: "reports", shortcut: "Ctrl+8" },
    { label: "Settings", target: "settings", icon: "settings", shortcut: "Ctrl+9" },
  ]},
];

const validPages = ["dashboard", "flocks", "daily-work", "feed-stock", "health", "production", "sales-money", "reports", "settings"];
const legacyRoutes = {
  houses: "flocks", batches: "flocks", "batch-dashboard": "flocks",
  operations: "daily-work", feed: "feed-stock", inventory: "feed-stock",
  eggs: "production", broiler: "production",
  sales: "sales-money", customers: "sales-money", expenses: "sales-money",
  "batch-reports": "reports",
};
function resolvePage(hash, startPage = "dashboard") {
  const candidate = String(hash || "").replace(/^#\/?/, "").split("/")[0];
  if (validPages.includes(candidate)) return candidate;
  if (legacyRoutes[candidate]) return legacyRoutes[candidate];
  if (validPages.includes(startPage)) return startPage;
  if (legacyRoutes[startPage]) return legacyRoutes[startPage];
  return "dashboard";
}

const pageTabs = {
  flocks: [
    { id: "batch-dashboard", label: "Batch dashboard" },
    { id: "flocks", label: "Batches & bird costs" },
    { id: "houses", label: "Houses / pens" },
  ],
  "daily-work": [
    { id: "daily-record", label: "Daily record" },
    { id: "mortality", label: "Mortality" },
    { id: "culling", label: "Culling" },
    { id: "water", label: "Water" },
  ],
  health: [
    { id: "health-record", label: "Health records" },
    { id: "drugs", label: "Drugs & treatments" },
    { id: "vaccination", label: "Vaccination" },
  ],
  "feed-stock": [
    { id: "feed", label: "Feed & usage" },
    { id: "purchases", label: "Purchases" },
    { id: "inventory", label: "Other stock" },
    { id: "suppliers", label: "Suppliers" },
  ],
  production: [
    { id: "eggs", label: "Egg production" },
    { id: "broiler", label: "Weight & growth" },
  ],
  "sales-money": [
    { id: "customers", label: "Customers" },
    { id: "sales", label: "Sales" },
    { id: "expenses", label: "Expenses" },
  ],
  reports: [
    { id: "farm-report", label: "Farm report" },
    { id: "batch-report", label: "Batch report" },
  ],
  settings: [
    { id: "farm", label: "Farm profile" },
    { id: "farm-defaults", label: "Farm defaults" },
    { id: "application", label: "App, appearance & account" },
    { id: "backup", label: "Backup & restore" },
    { id: "pricing", label: "Pricing" },
  ],
};
const defaultTabs = {
  dashboard: "overview", flocks: "batch-dashboard", "daily-work": "daily-record",
  health: "health-record", "feed-stock": "feed", production: "eggs",
  "sales-money": "customers", reports: "farm-report", settings: "farm",
};

function resolveTab(hash, page) {
  const requested = String(hash || "").replace(/^#\/?/, "").split("/")[1];
  const tabs = pageTabs[page] || [];
  if (tabs.some(tab => tab.id === requested)) return requested;
  return defaultTabs[page] || "overview";
}

// eslint-disable-next-line no-unused-vars
function Icon({ name }) {
  const paths = {
    dashboard: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
    houses: <><path d="m3 10 9-7 9 7" /><path d="M5 9v11h14V9" /><path d="M10 20v-6h4v6" /></>,
    batches: <><circle cx="8" cy="8" r="4" /><circle cx="16" cy="16" r="4" /><path d="M11 11l2 2" /></>,
    records: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 8h8M8 12h8M8 16h5" /></>,
    feed: <><path d="M4 8h16v12H4z" /><path d="M7 8V5h10v3M8 12h8M8 16h5" /></>,
    mortality: <><path d="M12 3c4 3 7 6 7 10a7 7 0 1 1-14 0c0-4 3-7 7-10Z" /><path d="m9 15 6-6M15 15 9 9" /></>,
    culling: <><path d="M6 4h12l1 17H5L6 4Z" /><path d="M9 4V2h6v2M8 8v9M12 8v9M16 8v9" /></>,
    health: <><path d="M12 21s8-4.5 8-11V5l-8-3-8 3v5c0 6.5 8 11 8 11Z" /><path d="M9 11h6M12 8v6" /></>,
    drugs: <><path d="M7 4h10v16H7z" /><path d="M9 8h6M9 12h6M9 16h4" /></>,
    vaccine: <><path d="m14 4 6 6-9 9-6-6 9-9Z" /><path d="m8 14-4 4M16 8l2-2" /><path d="m11 7 6 6" /></>,
    water: <><path d="M12 3s6 6 6 11a6 6 0 1 1-12 0c0-5 6-11 6-11Z" /></>,
    eggs: <><ellipse cx="12" cy="13" rx="7" ry="9" /><path d="M9 11c1-1 3-1 4 0" /></>,
    growth: <><path d="M4 19V5M4 19h16" /><path d="m7 15 4-4 3 2 5-7" /></>,
    inventory: <><path d="M4 7h16v13H4zM7 7V4h10v3" /><path d="M8 11h8M8 15h5" /></>,
    purchase: <><path d="M4 5h16v15H4z" /><path d="M8 5V3h8v2M8 10h8M8 14h5" /></>,
    supplier: <><circle cx="12" cy="7" r="3" /><path d="M5 21c.7-4 3-6 7-6s6.3 2 7 6" /></>,
    sales: <><path d="M4 19V5M4 19h16" /><path d="m7 15 3-3 3 2 5-6" /></>,
    customers: <><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3 21c.6-4 2.5-6 6-6s5.4 2 6 6M14 15c3.5 0 5.5 1.7 6 5" /></>,
    expenses: <><path d="M5 4h14v17H5z" /><path d="M8 9h8M8 13h8M8 17h5" /></>,
    reports: <><path d="M4 19V5M4 19h16" /><path d="M7 15v-4M11 15V8M15 15V6M19 15v-7" /></>,
    settings: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.8 1.8 0 0 0 .3 2l.1.1-1.7 1.7-.1-.1a1.8 1.8 0 0 0-2-.3 1.8 1.8 0 0 0-1 1.7v.2h-2.4v-.2a1.8 1.8 0 0 0-1-1.7 1.8 1.8 0 0 0-2 .3l-.1.1-1.7-1.7.1-.1a1.8 1.8 0 0 0 .3-2 1.8 1.8 0 0 0-1.7-1H6v-2.4h.2a1.8 1.8 0 0 0 1.7-1 1.8 1.8 0 0 0-.3-2l-.1-.1 1.7-1.7.1.1a1.8 1.8 0 0 0 2 .3 1.8 1.8 0 0 0 1-1.7V5h2.4v.2a1.8 1.8 0 0 0 1 1.7 1.8 1.8 0 0 0 2-.3l.1-.1 1.7 1.7-.1.1a1.8 1.8 0 0 0-.3 2 1.8 1.8 0 0 0 1.7 1h.2v2.4h-.2a1.8 1.8 0 0 0-1.7 1Z" /></>,
  };
  return <svg className="nav-glyph" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{paths[name] || paths.dashboard}</svg>;
}

function farmInitials(name) {
  const words = String(name || "").trim().split(/\s+/).filter(Boolean);
  return words.slice(0, 2).map(word => word[0]).join("").toUpperCase() || "GF";
}

function localDateLabel() {
  return new Intl.DateTimeFormat("en-NG", { day: "2-digit", month: "short", year: "numeric" }).format(new Date());
}

export default function AppShell({ farm, error, children, startPage = "dashboard", startPageReady = true }) {
  const [active, setActive] = useState(() => resolvePage(window.location.hash, startPage));
  const [activeTab, setActiveTab] = useState(() => resolveTab(window.location.hash, resolvePage(window.location.hash, startPage)));
  const [theme, setTheme] = useState(getThemePreference);
  const [backendConnected, setBackendConnected] = useState(null);
  const [hasUserNavigated, setHasUserNavigated] = useState(Boolean(window.location.hash));
  const mainContentRef = useRef(null);

  function changeTheme(event) {
    const nextTheme = setThemePreference(event.target.value);
    setTheme(nextTheme);
  }
  const today = useMemo(localDateLabel, []);

  function navigate(target) {
    const page = resolvePage(`#${target}`, startPage);
    const tab = defaultTabs[page] || "overview";
    setHasUserNavigated(true);
    if (window.location.hash !== `#${page}/${tab}`) window.location.hash = `${page}/${tab}`;
    setActive(page);
    setActiveTab(tab);
  }

  function navigateTab(tab) {
    const nextHash = `${active}/${tab}`;
    setHasUserNavigated(true);
    if (window.location.hash !== `#${nextHash}`) window.location.hash = nextHash;
    setActiveTab(tab);
  }

  useEffect(() => {
    const syncRoute = () => {
      const page = resolvePage(window.location.hash, startPage);
      setActive(page);
      setActiveTab(resolveTab(window.location.hash, page));
    };
    window.addEventListener("hashchange", syncRoute);
    if (window.location.hash) syncRoute();
    else if (startPageReady && !hasUserNavigated) {
      const initialPage = resolvePage("", startPage);
      window.location.hash = `${initialPage}/${defaultTabs[initialPage] || "overview"}`;
    }
    return () => window.removeEventListener("hashchange", syncRoute);
  }, [startPage, startPageReady, hasUserNavigated]);

  useEffect(() => {
    let mounted = true;
    const checkBackend = async () => {
      const connected = await connectionApi.check();
      if (mounted) setBackendConnected(connected);
    };
    checkBackend();
    const interval = window.setInterval(checkBackend, 15000);
    return () => {
      mounted = false;
      window.clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (!event.ctrlKey || event.altKey || event.shiftKey) return;
      const shortcuts = { "1": "dashboard", "2": "flocks", "3": "daily-work", "4": "feed-stock", "5": "health", "6": "production", "7": "sales-money", "8": "reports", "9": "settings" };
      const target = shortcuts[event.key];
      if (!target) return;
      event.preventDefault();
      window.location.hash = `${target}/${defaultTabs[target] || "overview"}`;
      setActive(target);
      setActiveTab(defaultTabs[target] || "overview");
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const activities = [
    { label: "Farm dashboard", target: "dashboard", icon: "dashboard" },
    { label: "Houses, pens and batches", target: "flocks", icon: "batches" },
    { label: "Daily operations", target: "daily-work", icon: "records" },
    { label: "Feed and inventory", target: "feed-stock", icon: "feed" },
    { label: "Health and vaccination", target: "health", icon: "health" },
    { label: "Production", target: "production", icon: "eggs" },
    { label: "Sales and finance", target: "sales-money", icon: "sales" },
  ];

  return (
    <div className="app-shell">
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          mainContentRef.current?.focus();
        }}
      >
        Skip to main content
      </a>
      <nav className="activity-bar" aria-label="Primary work areas">
        <div className="activity-bar-main">
          {activities.map(item => (
            <button
              key={item.target}
              type="button"
              className={active === item.target ? "activity-button active" : "activity-button"}
              aria-label={item.label}
              aria-current={active === item.target ? "page" : undefined}
              title={item.label}
              onClick={() => navigate(item.target)}
            >
              <Icon name={item.icon} />
            </button>
          ))}
        </div>
        <div className="activity-bar-footer">
          <button
            type="button"
            className={active === "settings" && activeTab === "application" ? "activity-button active" : "activity-button"}
            aria-label="Account"
            title="Account"
            onClick={() => { window.location.hash = "settings/application"; setActive("settings"); setActiveTab("application"); }}
          >
            <span className="activity-avatar" aria-hidden="true">{farmInitials(farm.name)}</span>
          </button>
          <button
            type="button"
            className={active === "settings" ? "activity-button active" : "activity-button"}
            aria-label="Settings"
            aria-current={active === "settings" ? "page" : undefined}
            title="Settings"
            onClick={() => navigate("settings")}
          >
            <Icon name="settings" />
          </button>
        </div>
      </nav>
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true"><img src={pomasLogo} alt="" draggable="false" /></div>
          <div className="brand-copy">
            <strong>Pomas</strong>
            <small>Farm management</small>
          </div>
        </div>

        <div className="workspace-summary" aria-label="Active farm">
          <span className="workspace-kicker">ACTIVE FARM</span>
          <span className="workspace-name">{farm.name || "Local farm"}</span>
          <span className="workspace-status">Local data · single farm</span>
        </div>

        <nav className="sidebar-nav" aria-label="Main navigation">
          {groups.map(group => (
            <div className="nav-group" key={group.label}>
              <p>{group.label}</p>
              {group.items.map(item => (
                <button
                  type="button"
                  className={active === item.target ? "nav-item active" : "nav-item"}
                  key={item.label}
                  onClick={() => navigate(item.target)}
                  title={item.shortcut ? `${item.label} · ${item.shortcut}` : item.label}
                  aria-label={item.label}
                >
                  <span className="nav-icon-wrap"><Icon name={item.icon} /></span>
                  <span className="nav-label">{item.label}</span>
                  {item.shortcut && <kbd>{item.shortcut.replace("Ctrl", "⌃")}</kbd>}
                </button>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="node-status">
            <span className={backendConnected === true ? "status-dot online" : backendConnected === false ? "status-dot offline" : "status-dot"} />
            <div><strong>{backendConnected === true ? "BACKEND CONNECTED" : backendConnected === false ? "BACKEND UNAVAILABLE" : "CHECKING BACKEND"}</strong><small>Local API connection</small></div>
          </div>
        </div>
      </aside>

      <main className="app-main" id="main-content" tabIndex={-1} ref={mainContentRef}>
        <div className="shell">
          <header className="app-topbar">
            <div className="topbar-title">
              <p className="eyebrow">{farm.name || "LOCAL FARM"} / POULTRY MANAGER</p>
              <h1>{farm.name}</h1>
              <p className="muted">{farm.location || "Farm profile"} · {farm.currency} · {farm.timezone}</p>
            </div>
            <div className="topbar-actions">
              <label className="theme-control">Theme<select value={theme} onChange={changeTheme}><option value="auto">Auto</option><option value="light">Light</option><option value="dark">Dark</option></select></label>
              <div className={backendConnected === true ? "system-pill connected" : backendConnected === false ? "system-pill disconnected" : "system-pill"} role="status" aria-live="polite">
                <span className={backendConnected === true ? "status-dot online" : backendConnected === false ? "status-dot offline" : "status-dot"} />
                {backendConnected === true ? "BACKEND CONNECTED" : backendConnected === false ? "BACKEND UNAVAILABLE" : "CHECKING BACKEND"}
              </div>
              <div className="system-pill" title="Designed to keep farm records on this computer">LOCAL-FIRST</div>
              <div className="date-chip">{today}</div>
            </div>
          </header>
          {error && <div className="error">{error}</div>}
          {(pageTabs[active] || []).length > 0 && (
            <nav className="page-tabs" aria-label={`${active} sections`}>
              {pageTabs[active].map(tab => (
                <button
                  key={tab.id}
                  type="button"
                  className={activeTab === tab.id ? "page-tab active" : "page-tab"}
                  aria-current={activeTab === tab.id ? "page" : undefined}
                  onClick={() => navigateTab(tab.id)}
                >
                  {tab.label}
                </button>
              ))}
            </nav>
          )}
          <NavigationContext.Provider value={{ activePage: active, activeTab }}>
            {children}
          </NavigationContext.Provider>
        </div>
      </main>

      <footer className="app-statusbar">
        <span><b>DATA MODE</b> · Local-first</span>
        <span><b>API</b> · {backendConnected === true ? "Connected" : backendConnected === false ? "Unavailable" : "Checking"}</span>
        <span className="statusbar-spacer" />
        <span><i className={backendConnected === true ? "status-dot online" : backendConnected === false ? "status-dot offline" : "status-dot"} /> {backendConnected === true ? "Backend responding" : backendConnected === false ? "Backend not responding" : "Checking connection"}</span>
      </footer>
    </div>
  );
}
