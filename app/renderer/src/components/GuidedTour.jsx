import { useEffect, useState } from "react";
import "./GuidedTour.css";

const sections = [
  { page: "dashboard", tab: "overview", title: "Dashboard", description: "Start here for farm-wide bird counts, production, feed, money, alerts, and recent changes. These figures come from saved records." },
  { page: "flocks", tab: "batch-dashboard", title: "Flocks", description: "Manage houses and pens, open permanent Layer or Broiler batches, and inspect each flock's population, costs, and status." },
  { page: "daily-work", tab: "daily-record", title: "Daily Work", description: "Record daily observations, water use, mortality, and culling against the correct batch and business date." },
  { page: "feed-stock", tab: "feed", title: "Feed & Stock", description: "Track feed types, purchases, feed usage, suppliers, and other farm supplies. Purchases and consumption are different events." },
  { page: "health", tab: "health-record", title: "Health", description: "Keep health observations, treatments, drugs, and vaccination records linked to the correct flock and date." },
  { page: "production", tab: "eggs", title: "Production", description: "Record egg collection and quality for layers, or weight and growth for broilers. The selected batch type determines the relevant workflow." },
  { page: "sales-money", tab: "customers", title: "Sales & Money", description: "Maintain customers, review the common sales ledger, and record farm or batch expenses." },
  { page: "reports", tab: "farm-report", title: "Reports", description: "Review farm-wide or batch-specific results for the selected date, then export the report after checking its context." },
  { page: "settings", tab: "farm", title: "Settings", description: "Configure the farm profile, operating defaults, account, appearance, backup options, and pricing preferences." },
];

const tabs = [
  { route: "flocks/houses", target: "tab-houses", title: "Houses and pens", description: "Create the physical houses or pens first. A batch must be assigned to a house or pen." },
  { route: "flocks/flocks", target: "tab-flocks", title: "Batches and bird costs", description: "Create a new batch for each flock placement and record its opening bird cost." },
  { route: "flocks/batch-dashboard", target: "tab-batch-dashboard", title: "Batch dashboard", description: "Inspect one flock's current population, feed, production, costs, and status." },
  { route: "daily-work/daily-record", target: "tab-daily-record", title: "Daily records", description: "Enter the daily observation for the selected batch and date." },
  { route: "daily-work/mortality", target: "tab-mortality", title: "Mortality", description: "Record deaths separately from culling and sales so the bird balance stays accurate." },
  { route: "daily-work/culling", target: "tab-culling", title: "Culling", description: "Record intentional removal separately from natural deaths." },
  { route: "daily-work/water", target: "tab-water", title: "Water", description: "Record water containers and counts using your farm's configured unit and container sizes." },
  { route: "feed-stock/feed", target: "tab-feed", title: "Feed and usage", description: "Set up feed types and record feed consumed by each batch." },
  { route: "feed-stock/purchases", target: "tab-purchases", title: "Feed purchases", description: "Record the supplier, date, quantity, and cost of feed received into stock." },
  { route: "feed-stock/inventory", target: "tab-inventory", title: "Other stock", description: "Track non-feed supplies with receipt and issue movements and clear reasons." },
  { route: "feed-stock/suppliers", target: "tab-suppliers", title: "Suppliers", description: "Maintain supplier details and reuse those records when recording purchases." },
  { route: "health/health-record", target: "tab-health-record", title: "Health records", description: "Record symptoms, observations, and the action taken for the selected batch." },
  { route: "health/drugs", target: "tab-drugs", title: "Drugs and treatments", description: "Keep treatment dates, quantities, reasons, and costs traceable." },
  { route: "health/vaccination", target: "tab-vaccination", title: "Vaccination", description: "Record vaccine, dose, quantity, date, and notes for the flock." },
  { route: "production/eggs", target: "tab-eggs", title: "Egg production", description: "For layer batches, record good and cracked eggs separately. Cracked eggs are not sellable." },
  { route: "production/broiler", target: "tab-broiler", title: "Weight and growth", description: "For broiler batches, enter sample weights and use the backend-calculated growth and feed conversion." },
  { route: "sales-money/customers", target: "tab-customers", title: "Customers", description: "Create reusable customer records to avoid retyping details for each sale." },
  { route: "sales-money/sales", target: "tab-sales", title: "Sales", description: "Review recorded egg and bird sales in the shared sales ledger." },
  { route: "sales-money/expenses", target: "tab-expenses", title: "Expenses", description: "Record each farm expense once with its original date, category, and amount." },
  { route: "reports/farm-report", target: "tab-farm-report", title: "Farm report", description: "Review the combined farm result for the selected as-of date." },
  { route: "reports/batch-report", target: "tab-batch-report", title: "Batch report", description: "Select a batch to inspect only that flock's records and results." },
  { route: "settings/farm", target: "tab-farm", title: "Farm profile", description: "Keep the farm name, location, timezone, and currency accurate." },
  { route: "settings/farm-defaults", target: "tab-farm-defaults", title: "Farm defaults", description: "Configure defaults such as crate size and water containers before entering records." },
  { route: "settings/application", target: "tab-application", title: "Application and account", description: "Manage account details, appearance, and the preferred start page." },
  { route: "settings/backup", target: "tab-backup", title: "Backup and restore", description: "Configure backups and verify that a backup can be restored before you need it." },
  { route: "settings/pricing", target: "tab-pricing", title: "Pricing", description: "Review the margin settings used when calculating broiler prices." },
];

const steps = [
  { target: "main-content", title: "Welcome to Pomas", description: "This interactive tour highlights the main navigation and the important sections inside it. Use Next to move around the app, or close the tour at any time.", route: null },
  ...sections.map(section => ({ target: `nav-${section.page}`, title: section.title, description: section.description, route: `${section.page}/${section.tab}` })),
  ...tabs.map(tab => ({ target: tab.target, title: tab.title, description: tab.description, route: tab.route })),
  { target: "main-content", title: "Ready to manage your farm", description: "Start with the farm profile, add houses or pens, create the current batch, and then enter daily records. Reopen this tour whenever you need a guided refresher.", route: null },
];

function findTarget(name) {
  return document.querySelector(`[data-tour="${name}"]`);
}

export default function GuidedTour({ open, onClose, onNavigate }) {
  const [index, setIndex] = useState(0);
  const [rect, setRect] = useState(null);
  const step = steps[index];

  useEffect(() => {
    if (!open) return undefined;
    if (step?.route) onNavigate(step.route);
    let frame = 0;
    let timer = 0;
    const update = () => {
      const target = findTarget(step.target);
      if (!target) { setRect(null); return; }
      const bounds = target.getBoundingClientRect();
      setRect({ top: bounds.top - 5, left: bounds.left - 5, width: bounds.width + 10, height: bounds.height + 10 });
    };
    frame = window.requestAnimationFrame(update);
    timer = window.setTimeout(update, 160);
    window.addEventListener("resize", update);
    window.addEventListener("scroll", update, true);
    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(timer);
      window.removeEventListener("resize", update);
      window.removeEventListener("scroll", update, true);
    };
  }, [open, index, step, onNavigate]);

  useEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const keydown = event => {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowRight") setIndex(current => Math.min(steps.length - 1, current + 1));
      if (event.key === "ArrowLeft") setIndex(current => Math.max(0, current - 1));
    };
    window.addEventListener("keydown", keydown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", keydown);
    };
  }, [open, onClose]);

  useEffect(() => { if (open) setIndex(0); }, [open]);

  if (!open) return null;
  const finish = () => { setRect(null); onClose(); };
  const next = () => { if (index === steps.length - 1) finish(); else setIndex(current => current + 1); };

  return (
    <div className="guided-tour-layer" role="dialog" aria-modal="true" aria-labelledby="guided-tour-title" aria-describedby="guided-tour-description">
      {rect && <div className="guided-tour-spotlight" style={rect} aria-hidden="true" />}
      <section className="guided-tour-card">
        <div className="guided-tour-topline"><span>POMAS GUIDED TOUR</span><button type="button" onClick={finish} aria-label="Close tour">×</button></div>
        <div className="guided-tour-progress"><span style={{ width: `${((index + 1) / steps.length) * 100}%` }} /></div>
        <p className="guided-tour-count">STEP {index + 1} OF {steps.length}</p>
        <h2 id="guided-tour-title">{step.title}</h2>
        <p id="guided-tour-description">{step.description}</p>
        <div className="guided-tour-actions">
          <button type="button" className="guided-tour-skip" onClick={finish}>Skip tour</button>
          <div>
            <button type="button" className="guided-tour-back" onClick={() => setIndex(current => Math.max(0, current - 1))} disabled={index === 0}>Back</button>
            <button type="button" className="guided-tour-next" onClick={next}>{index === steps.length - 1 ? "Finish tour" : "Next"}</button>
          </div>
        </div>
        <p className="guided-tour-hint">Press Esc to exit. Arrow keys also work.</p>
      </section>
    </div>
  );
}
