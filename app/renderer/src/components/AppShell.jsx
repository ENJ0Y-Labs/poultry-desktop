import { useEffect, useMemo, useRef, useState } from "react";
// eslint-disable-next-line no-unused-vars
import { NavigationContext } from "./NavigationContext.jsx";
import { connectionApi } from "../services/api.js";
import { appVersionLabel } from "../utils/appInfo.js";
import pomasLogo from "../assets/pomas-logo.png";
// eslint-disable-next-line no-unused-vars
import PageHelp from "./PageHelp.jsx";
// eslint-disable-next-line no-unused-vars
import GuidedTour from "./GuidedTour.jsx";
// eslint-disable-next-line no-unused-vars
import QuickActionsMenu from "./QuickActionsMenu.jsx";
// eslint-disable-next-line no-unused-vars
import { Modal } from "./Modal.jsx";
import { registerGlobalShortcuts } from "../utils/keyboardShortcuts.js";
import { attentionTarget } from "../utils/attentionNavigation.js";
// eslint-disable-next-line no-unused-vars
import DesktopTitleBar from "./DesktopTitleBar.jsx";

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
    { label: "Import / Export", target: "import-export", icon: "importExport", shortcut: "Ctrl+9" },
    { label: "Settings", target: "settings", icon: "settings", shortcut: "Ctrl+0" },
  ]},
];

const validPages = ["dashboard", "flocks", "daily-work", "feed-stock", "health", "production", "sales-money", "reports", "import-export", "settings"];
const validStartPages = ["dashboard", "flocks", "daily-work", "feed-stock", "health", "production", "sales-money", "reports", "settings"];
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
  if (validStartPages.includes(startPage)) return startPage;
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
    { id: "sales", label: "Sales" },
    { id: "customers", label: "Customers" },
    { id: "expenses", label: "Expenses" },
  ],
  reports: [
    { id: "farm-report", label: "Farm report" },
    { id: "batch-report", label: "Batch report" },
    { id: "batch-comparison", label: "Compare batches" },
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
  "sales-money": "sales", reports: "farm-report", "import-export": "overview", settings: "farm",
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
    importExport: <><path d="M7 7h12l-3-3M19 7l-3 3" /><path d="M17 17H5l3 3M5 17l3-3" /></>,
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

// eslint-disable-next-line no-unused-vars
function SidebarToggleIcon({ expanded }) {
  return (
    <svg
      className="sidebar-toggle-icon"
      data-mode={expanded ? "expanded" : "collapsed"}
      viewBox="0 0 24 24"
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M9 4v16" />
      {expanded ? <path d="m15 9-3 3 3 3" /> : <path d="m13 9 3 3-3 3" />}
    </svg>
  );
}

export default function AppShell({ farm, error, appInfo, onRetry, onQuickAction, children, batches = [], selectedBatchId = "", feedTypes = [], feedInventory = [], inventoryItems = [], attention = [], startPage = "dashboard", startPageReady = true }) {
  const [active, setActive] = useState(() => resolvePage(window.location.hash, startPage));
  const [activeTab, setActiveTab] = useState(() => resolveTab(window.location.hash, resolvePage(window.location.hash, startPage)));
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => localStorage.getItem("pomas:sidebar-collapsed") === "true");
  const [attentionCollapsed, setAttentionCollapsed] = useState(() => localStorage.getItem("pomas:attention-collapsed") === "true");
  const [attentionMobileOpen, setAttentionMobileOpen] = useState(false);
  const [backendConnected, setBackendConnected] = useState(null);
  const [recoveryNeeded, setRecoveryNeeded] = useState(false);
  const [tourOpen, setTourOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [hasUserNavigated, setHasUserNavigated] = useState(Boolean(window.location.hash));
  const mainContentRef = useRef(null);

  async function retryBackend() {
    if (!onRetry || retrying) return;
    setRetrying(true);
    try {
      const recovered = await onRetry();
      if (recovered !== false) setRecoveryNeeded(false);
    } finally {
      setRetrying(false);
    }
  }

  const today = useMemo(localDateLabel, []);
  const activeBatch = batches.find(batch => batch.id === selectedBatchId && batch.status === "ACTIVE") || null;
  const hasActiveFeedTypes = feedTypes.some(type => type.status === "ACTIVE");
  const hasApplicableFeed = Boolean(activeBatch && feedTypes.some(type =>
    type.status === "ACTIVE"
    && (type.applicableTypes || []).includes(activeBatch.type)
    && feedInventory.some(item => item.feedTypeId === type.id && Number(item.remainingQuantityMilli) > 0)
  ));
  const hasActiveInventoryItems = inventoryItems.some(item => item.status === "ACTIVE");

  useEffect(() => {
    localStorage.setItem("pomas:sidebar-collapsed", String(sidebarCollapsed));
  }, [sidebarCollapsed]);
  useEffect(() => {
    localStorage.setItem("pomas:attention-collapsed", String(attentionCollapsed));
  }, [attentionCollapsed]);
  const activePageLabel = groups.flatMap(group => group.items).find(item => item.target === active)?.label || "Dashboard";
  const activeTabLabel = (pageTabs[active] || []).find(tab => tab.id === activeTab)?.label;
  const actionableAttention = attention.filter(item => attentionTarget(item));
  const attentionCount = actionableAttention.length;
  const customTitleBar = Boolean(appInfo?.customTitleBar && window.poultryDesktop?.quit);
  const databaseStatus = recoveryNeeded
    ? backendConnected ? "Connected · refresh needed" : "Offline"
    : backendConnected ? "Connected" : "Connecting";
  function navigate(target) {
    const page = resolvePage(`#${target}`, startPage);
    const tab = defaultTabs[page] || "overview";
    setHasUserNavigated(true);
    if (window.location.hash !== `#${page}/${tab}`) window.location.hash = `${page}/${tab}`;
    setActive(page);
    setActiveTab(tab);
  }

  function navigateQuickAction(target) {
    const [page, tab] = target.replace(/^#/, "").split("/");
    setHasUserNavigated(true);
    window.location.hash = target.replace(/^#/, "");
    setActive(page);
    setActiveTab(tab);
  }

  function navigateTourTarget(target) {
    const [page, tab] = target.split("/");
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
      if (mounted) {
        setBackendConnected(connected);
        if (!connected) setRecoveryNeeded(true);
      }
    };
    const onBackendUnavailable = () => {
      if (!mounted) return;
      setBackendConnected(false);
      setRecoveryNeeded(true);
    };
    window.addEventListener("pomas:backend-unavailable", onBackendUnavailable);
    checkBackend();
    const interval = window.setInterval(checkBackend, 5000);
    return () => {
      mounted = false;
      window.removeEventListener("pomas:backend-unavailable", onBackendUnavailable);
      window.clearInterval(interval);
    };
  }, []);

  useEffect(() => registerGlobalShortcuts({
    navigate: target => {
      const tab = defaultTabs[target] || "overview";
      window.location.hash = "#" + target + "/" + tab;
      setActive(target);
      setActiveTab(tab);
    },
    openQuickActions: () => window.dispatchEvent(new CustomEvent("pomas:open-quick-actions")),
    focusPageSearch: () => {
      const search = mainContentRef.current?.querySelector('input[type="search"]');
      if (!search) return false;
      search.focus();
      search.select?.();
      return true;
    },
  }), []);


  return (
    <div className={`app-shell${sidebarCollapsed ? " sidebar-collapsed" : ""}${attentionCollapsed ? " attention-collapsed" : ""}${attentionMobileOpen ? " attention-open" : ""}${customTitleBar ? " desktop-titlebar-enabled" : ""}`}>
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
      {customTitleBar && <DesktopTitleBar
        onNavigate={navigateQuickAction}
        onQuickActions={() => window.dispatchEvent(new CustomEvent("pomas:open-quick-actions"))}
        onToggleSidebar={() => setSidebarCollapsed(value => !value)}
        onToggleAttention={() => {
          if (window.innerWidth <= 1150) setAttentionMobileOpen(value => !value);
          else setAttentionCollapsed(value => !value);
        }}
        onShortcuts={() => setShortcutsOpen(true)}
        sidebarCollapsed={sidebarCollapsed}
        attentionCollapsed={attentionCollapsed}
      />}
      <aside className={sidebarCollapsed ? "sidebar collapsed" : "sidebar"}>
        <div className="brand" data-tour="brand">
          <div className="brand-mark" aria-hidden="true">
            <img src={pomasLogo} alt="" draggable="false" />
          </div>
          <div className="brand-copy">
            <strong>Pomas</strong>
            <small>Farm management</small>
          </div>
          <button
            type="button"
            className="sidebar-toggle"
            aria-label={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            aria-expanded={!sidebarCollapsed}
            title={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            onClick={() => setSidebarCollapsed(value => !value)}
          >
            <SidebarToggleIcon expanded={!sidebarCollapsed} />
          </button>
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
                  data-tour={`nav-${item.target}`}
                  aria-current={active === item.target ? "page" : undefined}
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
          <button
            type="button"
            className="nav-item"
            aria-label="Account"
            title="Account"
            onClick={() => { window.location.hash = "settings/application"; setActive("settings"); setActiveTab("application"); }}
          >
            <span className="nav-icon-wrap"><span className="activity-avatar" aria-hidden="true">{farmInitials(farm.name)}</span></span>
            <span className="nav-label">Account</span>
          </button>
        </div>
      </aside>

      <main className="app-main" id="main-content" tabIndex={-1} ref={mainContentRef} data-tour="main-content">
        <div className="shell">
          <header className="app-topbar">
            <div className="topbar-title">
              <p className="eyebrow">{farm.name || "LOCAL FARM"} / {activePageLabel.toUpperCase()}</p>
              <h1>{activeTabLabel || activePageLabel}</h1>
              <p className="muted">{farm.location || "Farm profile"} · {farm.currency} · {farm.timezone}</p>
            </div>
            <div className="topbar-actions">
              <button type="button" className="secondary-button attention-mobile-toggle" onClick={() => setAttentionMobileOpen(true)} aria-label={`Open attention panel, ${attentionCount} actionable alerts`} aria-expanded={attentionMobileOpen}>Attention <span>{attentionCount}</span></button>
              <QuickActionsMenu activeBatch={activeBatch} hasFeedTypes={hasActiveFeedTypes} hasApplicableFeed={hasApplicableFeed} hasInventoryItems={hasActiveInventoryItems} onAction={onQuickAction} onNavigate={navigateQuickAction} />
              <button type="button" className="secondary-button keyboard-shortcuts-trigger" onClick={() => setShortcutsOpen(true)} aria-haspopup="dialog">Keyboard shortcuts</button>
              <div className="date-chip">{today}</div>
            </div>
          </header>
          {error && <div className="error" role="alert">{error}</div>}
          {recoveryNeeded && (
            <div className="error connection-recovery" role="alert">
              {backendConnected === true
                ? "The local backend is responding again, but farm data has not been refreshed. Your unsaved form entries are still on this screen."
                : "The local backend is unavailable. Farm records remain in the local database. Your unsaved form entries are still on this screen."}
              <button type="button" disabled={retrying} onClick={retryBackend}>
                {retrying ? "Retrying..." : "Retry connection"}
              </button>
            </div>
          )}
          <PageHelp page={active} tab={activeTab} onStartTour={() => setTourOpen(true)} />
          {(pageTabs[active] || []).length > 0 && (
            <nav className="page-tabs" aria-label={`${active} sections`} data-tour="page-tabs">
              {pageTabs[active].map(tab => (
                <button
                  key={tab.id}
                  type="button"
                  className={activeTab === tab.id ? "page-tab active" : "page-tab"}
                  data-tour={`tab-${tab.id}`}
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
        <Modal open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} title="Keyboard shortcuts" description="Shortcuts available in Pomas. Native browser and operating-system commands are left untouched." size="medium" footer={<div className="pomas-modal-actions"><button type="button" className="secondary-button" onClick={() => setShortcutsOpen(false)}>Close</button></div>}>
          <div className="keyboard-shortcut-reference">
            <div><span>Open quick actions</span><kbd>Ctrl + K</kbd></div>
            <div><span>Focus page record search</span><kbd>Ctrl + F</kbd></div>
            {groups[0].items.map(item => <div key={item.target}><span>Go to {item.label}</span><kbd>{item.shortcut}</kbd></div>)}
            <div><span>Close active modal or drawer</span><kbd>Esc</kbd></div>
            <p>Ctrl + N and Ctrl + S are not intercepted. They retain native behavior until a single safe app-wide create/save action exists.</p>
          </div>
        </Modal>
        <GuidedTour open={tourOpen} onClose={() => setTourOpen(false)} onNavigate={navigateTourTarget} />
      </main>

      {attentionMobileOpen && <button type="button" className="attention-backdrop" aria-label="Close attention panel" onClick={() => setAttentionMobileOpen(false)} />}
      <aside className={`attention-sidebar${attentionCollapsed ? " collapsed" : ""}`} aria-label="Farm attention panel">
        <header className="attention-sidebar-header">
          <div className="attention-sidebar-heading"><h2>Attention</h2><span className="attention-count">{attentionCount}</span></div>
          <button type="button" className="attention-panel-toggle" onClick={() => { setAttentionCollapsed(value => !value); setAttentionMobileOpen(false); }} aria-label={attentionCollapsed ? "Expand attention panel" : "Collapse attention panel"} aria-expanded={!attentionCollapsed} title={attentionCollapsed ? "Expand attention panel" : "Collapse attention panel"}>
            <span aria-hidden="true">{attentionCollapsed ? "‹" : "›"}</span>
          </button>
          <button type="button" className="attention-panel-close" onClick={() => setAttentionMobileOpen(false)} aria-label="Close attention panel">×</button>
        </header>
        <div className="attention-sidebar-content">
          {attentionCount === 0 ? <p className="muted">Nothing currently requires attention.</p> : <>
            {[["CRITICAL", "Critical"], ["WARNING", "Warnings"], ["INFO", "Information"]].map(([severity, label]) => {
              const items = actionableAttention.filter(item => String(item.severity).toUpperCase() === severity);
              return items.length > 0 && <section className="attention-severity-group" key={severity}>
                <h3>{label}<span>{items.length}</span></h3>
                <div className="attention-list">{items.map((item, index) => {
                  const target = attentionTarget(item);
                  const context = item.batchCode || item.item || "";
                  return <button type="button" className="attention-item attention-item-clickable" key={`${item.type}-${item.batchId || item.itemId || index}`} onClick={() => { navigateQuickAction(target); setAttentionMobileOpen(false); }} aria-label={`${item.title}. ${item.message}. Open related record`}>
                    <span className={`severity ${String(item.severity).toLowerCase()}`}>{item.severity}</span>
                    <span className="attention-item-copy"><strong>{item.title}</strong><span>{item.message}</span>{context && <span className="attention-item-meta">{context}</span>}</span>
                  </button>;
                })}</div>
              </section>;
            })}
            <button type="button" className="attention-view-all" onClick={() => navigate("dashboard")}>Review farm dashboard <span aria-hidden="true">→</span></button>
          </>}
        </div>
      </aside>

      <footer className="system-status-bar" aria-label="System status">
        <span className={backendConnected === true && !recoveryNeeded ? "system-status-connected" : backendConnected === false || recoveryNeeded ? "system-status-disconnected" : "system-status-connecting"} role="status" aria-live="polite"><i />Database · {databaseStatus}</span>
        <span>Local mode</span>
        <span className="system-status-version">{appVersionLabel(appInfo)}</span>
      </footer>

    </div>
  );
}
