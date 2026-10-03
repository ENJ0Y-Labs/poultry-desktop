import { useEffect, useState } from "react";

const groups = [
  { label: "Overview", items: [{ label: "Dashboard", target: "dashboard" }] },
  { label: "Farm", items: [
    { label: "Houses / Pens", target: "houses" },
    { label: "Batches / Flocks", target: "batches" },
  ]},
  { label: "Operations", items: [
    { label: "Daily Records", target: "operations" },
    { label: "Feed", target: "feed" },
    { label: "Mortality", target: "operations" },
    { label: "Culling", target: "operations" },
    { label: "Health", target: "health" },
    { label: "Drugs", target: "health" },
    { label: "Vaccination", target: "health" },
    { label: "Water", target: "operations" },
  ]},
  { label: "Production", items: [
    { label: "Eggs", target: "eggs" },
    { label: "Weight & Growth", target: "broiler" },
  ]},
  { label: "Inventory", items: [
    { label: "Stock", target: "inventory" },
    { label: "Purchases", target: "feed" },
    { label: "Suppliers", target: "feed" },
  ]},
  { label: "Commerce", items: [
    { label: "Sales", target: "sales" },
    { label: "Customers", target: "customers" },
    { label: "Expenses", target: "expenses" },
  ]},
  { label: "Reports", items: [{ label: "Reports", target: "reports" }] },
  { label: "Settings", items: [{ label: "Settings", target: "settings" }] },
];

export default function AppShell({ farm, error, children, startPage = "dashboard" }) {
  const [active, setActive] = useState(startPage);

  useEffect(() => { document.getElementById(startPage)?.scrollIntoView({ behavior: "auto", block: "start" }); }, [startPage]);

  function navigate(target) {
    setActive(target);
    document.getElementById(target)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  useEffect(() => {
    const onScroll = () => {
      const targets = ["dashboard", "houses", "batches", "operations", "feed", "health", "eggs", "broiler", "inventory", "sales", "customers", "expenses", "settings"];
      const current = targets.reduce((best, id) => {
        const element = document.getElementById(id);
        if (!element) return best;
        const distance = Math.abs(element.getBoundingClientRect().top - 96);
        return distance < best.distance ? { id, distance } : best;
      }, { id: "dashboard", distance: Number.POSITIVE_INFINITY });
      setActive(current.id);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">GF</span>
          <div><strong>Grantino Farms</strong><small>Poultry Manager</small></div>
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
                >
                  <span>{item.label}</span>
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="status-dot" />
          <span>Local database</span>
        </div>
      </aside>

      <main className="app-main">
        <div className="shell">
          <header className="topbar app-topbar">
            <div>
              <p className="eyebrow">GRANTINO FARMS</p>
              <h1>{farm.name}</h1>
              <p className="muted">{farm.location || "Farm profile"} · {farm.currency} · {farm.timezone}</p>
            </div>
            <div className="connection-status">Offline-first</div>
          </header>
          {error && <div className="error">{error}</div>}
          {children}
          <section id="reports" className="card full shell-placeholder">
            <div className="section-head"><h2>Reports</h2><p className="muted">Report pages are reserved for Phase 22+ report views. The shell is ready for backend-backed reporting without inventing figures in the renderer.</p></div>
          </section>
        </div>
      </main>
    </div>
  );
}
