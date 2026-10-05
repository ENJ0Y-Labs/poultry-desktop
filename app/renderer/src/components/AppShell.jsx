import { useEffect, useMemo, useState } from "react";

const groups = [
  { label: "Overview", items: [{ label: "Dashboard", target: "dashboard", icon: "dashboard", shortcut: "Ctrl+1" }] },
  { label: "Farm", items: [
    { label: "Houses / Pens", target: "houses", icon: "houses" },
    { label: "Batches / Flocks", target: "batches", icon: "batches", shortcut: "Ctrl+2" },
  ]},
  { label: "Operations", items: [
    { label: "Daily Records", target: "operations", icon: "records", shortcut: "Ctrl+3" },
    { label: "Feed", target: "feed", icon: "feed" },
    { label: "Mortality", target: "operations", icon: "mortality" },
    { label: "Culling", target: "operations", icon: "culling" },
    { label: "Health", target: "health", icon: "health" },
    { label: "Drugs", target: "health", icon: "drugs" },
    { label: "Vaccination", target: "health", icon: "vaccine" },
    { label: "Water", target: "operations", icon: "water" },
  ]},
  { label: "Production", items: [
    { label: "Eggs", target: "eggs", icon: "eggs", shortcut: "Ctrl+4" },
    { label: "Weight & Growth", target: "broiler", icon: "growth" },
  ]},
  { label: "Inventory", items: [
    { label: "Stock", target: "inventory", icon: "inventory" },
    { label: "Purchases", target: "feed", icon: "purchase" },
    { label: "Suppliers", target: "feed", icon: "supplier" },
  ]},
  { label: "Commerce", items: [
    { label: "Sales", target: "sales", icon: "sales", shortcut: "Ctrl+5" },
    { label: "Customers", target: "customers", icon: "customers" },
    { label: "Expenses", target: "expenses", icon: "expenses" },
  ]},
  { label: "Reports", items: [{ label: "Reports", target: "reports", icon: "reports", shortcut: "Ctrl+6" }] },
  { label: "Settings", items: [{ label: "Settings", target: "settings", icon: "settings", shortcut: "Ctrl+7" }] },
];

const targets = ["dashboard", "houses", "batches", "operations", "feed", "health", "eggs", "broiler", "inventory", "sales", "customers", "expenses", "reports", "settings"];

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

function localDateLabel() {
  return new Intl.DateTimeFormat("en-NG", { day: "2-digit", month: "short", year: "numeric" }).format(new Date());
}

export default function AppShell({ farm, error, children, startPage = "dashboard" }) {
  const [active, setActive] = useState(startPage);
  const today = useMemo(localDateLabel, []);

  function navigate(target) {
    setActive(target);
    document.getElementById(target)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  useEffect(() => {
    const onScroll = () => {
      const current = targets.reduce((best, id) => {
        const element = document.getElementById(id);
        if (!element) return best;
        const distance = Math.abs(element.getBoundingClientRect().top - 110);
        return distance < best.distance ? { id, distance } : best;
      }, { id: "dashboard", distance: Number.POSITIVE_INFINITY });
      setActive(current.id);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (!event.ctrlKey || event.altKey || event.shiftKey) return;
      const shortcuts = { "1": "dashboard", "2": "batches", "3": "operations", "4": "eggs", "5": "sales", "6": "reports", "7": "settings" };
      const target = shortcuts[event.key];
      if (!target) return;
      event.preventDefault();
      navigate(target);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true"><span>GF</span></div>
          <div className="brand-copy">
            <strong>Grantino Farms</strong>
            <small>Poultry Farm Manager</small>
          </div>
        </div>

        <div className="workspace-switcher">
          <span className="workspace-kicker">WORKSPACE</span>
          <span className="workspace-name">LOCAL FARM</span>
          <span className="workspace-status"><i /> SQLite · Offline first</span>
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
                >
                  <span className="nav-icon-wrap">{Icon({ name: item.icon })}</span>
                  <span className="nav-label">{item.label}</span>
                  {item.shortcut && <kbd>{item.shortcut.replace("Ctrl", "⌃")}</kbd>}
                </button>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="node-status">
            <span className="status-dot online" />
            <div><strong>LOCAL DATABASE</strong><small>127.0.0.1 · Spring Boot</small></div>
          </div>
          <span className="pulse" aria-hidden="true" />
        </div>
      </aside>

      <main className="app-main">
        <div className="shell">
          <header className="app-topbar">
            <div className="topbar-title">
              <p className="eyebrow">GRANTINO FARMS / POULTRY MANAGER</p>
              <h1>{farm.name}</h1>
              <p className="muted">{farm.location || "Farm profile"} · {farm.currency} · {farm.timezone}</p>
            </div>
            <div className="topbar-actions">
              <div className="system-pill"><span className="status-dot online" /> BACKEND ONLINE</div>
              <div className="system-pill"><span className="status-dot cyan" /> OFFLINE-FIRST</div>
              <div className="date-chip">{today}</div>
            </div>
          </header>
          {error && <div className="error">{error}</div>}
          {children}
        </div>
      </main>

      <footer className="app-statusbar">
        <span><b>LOCAL-DB</b> · SQLite</span>
        <span><b>API</b> · 127.0.0.1:18942</span>
        <span className="statusbar-spacer" />
        <span><i className="status-dot online" /> Data stays on this PC</span>
      </footer>
    </div>
  );
}
