import { useContext, useState } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
import { formatAuditAction, formatAuditEntity, formatAuditTimestamp } from "../utils/auditFormatting.js";
import { attentionTarget } from "../utils/attentionNavigation.js";

function formatBusinessDate(value, pattern = "YYYY-MM-DD") {
  if (!value || typeof value !== "string") return "—";
  pattern = pattern || "YYYY-MM-DD";
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return value;

  const [, year, month, day] = match;
  const parts = { YYYY: year, MM: month, DD: day };
  return pattern.replace(/YYYY|MM|DD/g, token => parts[token]);
}

const primaryMetrics = new Set(["Total birds", "Revenue", "Profit"]);

export default function DashboardPage() {
  const {
    formatMoney,
    formatQuantity,
    formatCount,
    farm,
    houses,
    batches,
    feedTypes,
    inventoryItems,
    dashboard,
    attention,
    auditRows,
    applicationSettings,
  } = useContext(FarmManagerContext);

  const isFirstRunEmpty = dashboard
    && houses.length === 0
    && batches.length === 0
    && Number(dashboard.totalBirds || 0) === 0
    && Number(dashboard.activeBatches || 0) === 0
    && Number(dashboard.revenueMinor || 0) === 0
    && Number(dashboard.expensesMinor || 0) === 0
    && Number(dashboard.profitMinor || 0) === 0
    && Number(dashboard.eggsGood || 0) === 0
    && Number(dashboard.feedSummary?.typeCount || 0) === 0
    && auditRows.length === 0;

  function navigateTo(target) {
    window.location.hash = target;
  }

  const [checklistDismissed, setChecklistDismissed] = useState(
    () => localStorage.getItem("pomas:getting-started-dismissed") === "true"
  );
  const checklistItems = [
    { id: "house", label: "Create a house or pen", description: "Set up the physical location for a flock.", done: houses.length > 0, target: "flocks/houses", action: "Manage houses" },
    { id: "batch", label: "Open a batch", description: "Record flock type, placement date, and starting bird count.", done: batches.length > 0, target: "flocks/flocks", action: "Manage batches" },
    { id: "feed", label: "Set up feed", description: "Create at least one feed type before recording purchases and usage.", done: feedTypes.length > 0, target: "feed-stock/feed", action: "Set up feed" },
    { id: "stock", label: "Add a farm supply item", description: "Create a stock item so drugs, vaccines, and supplies can be tracked.", done: inventoryItems.length > 0, target: "feed-stock/inventory", action: "Set up stock" },
  ];
  const checklistComplete = checklistItems.every(item => item.done);
  function dismissChecklist() {
    localStorage.setItem("pomas:getting-started-dismissed", "true");
    setChecklistDismissed(true);
  }

  return (
    <div className="grid">
      <PageSection page="dashboard" tab="overview"><section id="dashboard" className="card full dashboard-page">
        <div className="section-head">
          <div><p className="eyebrow">OVERVIEW</p><h2>Farm dashboard</h2></div>
          <p className="dashboard-as-of muted">
            <span>Dashboard as of</span>
            <strong>{dashboard ? formatBusinessDate(dashboard.asOf, applicationSettings?.dateFormat) : "Loading..."}</strong>
          </p>
        </div>
        {dashboard && !checklistDismissed && !checklistComplete && !isFirstRunEmpty && (
          <section className="getting-started-checklist subsection" aria-labelledby="getting-started-title">
            <div className="section-head">
              <div>
                <p className="eyebrow">GETTING STARTED</p>
                <h3 id="getting-started-title">Finish setting up your farm</h3>
                <p className="muted">Progress is calculated from saved farm records, not from which screens you have opened.</p>
              </div>
              <button type="button" className="secondary-button" onClick={dismissChecklist}>Dismiss checklist</button>
            </div>
            <ol className="getting-started-list">
              {checklistItems.map(item => (
                <li key={item.id} className={item.done ? "getting-started-item complete" : "getting-started-item"}>
                  <span className="getting-started-status" aria-label={item.done ? "Complete" : "Not complete"}>{item.done ? "✓" : "○"}</span>
                  <div className="getting-started-item-copy">
                    <strong>{item.label}</strong>
                    <p>{item.description}</p>
                  </div>
                  {!item.done && <button type="button" className="text-button" onClick={() => navigateTo(item.target)}>{item.action}</button>}
                </li>
              ))}
            </ol>
          </section>
        )}
        {dashboard ? isFirstRunEmpty ? (
          <section className="first-run-empty" aria-labelledby="first-run-title">
            <p className="eyebrow">GETTING STARTED</p>
            <h3 id="first-run-title">Your farm is ready to be set up</h3>
            <p className="first-run-intro">These zeros are expected because no flock records have been entered yet. Start with your farm's physical setup, then add birds and record daily work. Nothing has been added automatically.</p>
            <button type="button" className="secondary-button" onClick={dismissChecklist}>Dismiss getting-started guidance</button>
            <ol className="first-run-steps">
              <li><span className="first-run-step-number">1</span><div><strong>Create a house or pen</strong><p>Add the place where your birds will be kept. A house is required before you can create a batch.</p><button type="button" onClick={() => navigateTo("flocks/houses")}>Create house / pen</button></div></li>
              <li><span className="first-run-step-number">2</span><div><strong>Open your first batch</strong><p>Record whether the flock is for layers or broilers, its placement date, and starting bird count.</p><button type="button" onClick={() => navigateTo("flocks/flocks")}>Create batch</button></div></li>
              <li><span className="first-run-step-number">3</span><div><strong>Set up and record feed</strong><p>Add a feed type, record a purchase, then record feed used by an active batch.</p><button type="button" onClick={() => navigateTo("feed-stock/feed")}>Open feed & stock</button></div></li>
              <li><span className="first-run-step-number">4</span><div><strong>Start daily records</strong><p>Record routine observations for your active batch. Mortality, water, health, and production can be entered in their own sections.</p><button type="button" onClick={() => navigateTo("daily-work/daily-record")}>Record daily work</button></div></li>
            </ol>
          </section>
        ) : (
          <>
            <div className="metric-grid">
              {[
                ["Total birds", formatCount(dashboard.totalBirds), ""],
                ["Layer birds", formatCount(dashboard.layerBirds), ""],
                ["Broiler birds", formatCount(dashboard.broilerBirds), ""],
                ["Active batches", formatCount(dashboard.activeBatches), ""],
                ["Mortality", formatCount(dashboard.mortality), "birds"],
                ["Good eggs", formatCount(dashboard.eggsGood), "eggs"],
                ["Revenue", formatMoney(dashboard.revenueMinor, farm.currency), ""],
                ["Expenses", formatMoney(dashboard.expensesMinor, farm.currency), ""],
                ["Profit", formatMoney(dashboard.profitMinor, farm.currency), ""],
                ["Inventory alerts", dashboard.inventoryAlerts, ""],
              ].map(([label, value, unit]) => (
                <div
                  className={"metric-card" + (primaryMetrics.has(label) ? " metric-card-primary" : "")}
                  key={label}
                >
                  <span>{label}</span>
                  <strong>{value ?? "—"}</strong>
                  {unit && <small>{unit}</small>}
                </div>
              ))}
            </div>
            <div className="dashboard-columns">
              <div className="subsection dashboard-feed-panel"><h3>Feed</h3><div className="dashboard-list">
                <div><span>Feed types</span><strong>{dashboard.feedSummary?.typeCount ?? 0}</strong></div>
                <div><span>Stock remaining</span><strong>{formatQuantity(dashboard.feedSummary?.remainingQuantityMilli)}</strong></div>
                <div><span>Stock value</span><strong>{formatMoney(dashboard.feedSummary?.remainingCostMinor, farm.currency)}</strong></div>
                <div><span>Consumed cost</span><strong>{formatMoney(dashboard.feedSummary?.consumedCostMinor, farm.currency)}</strong></div>
              </div></div>
              <div className="subsection dashboard-attention-panel"><h3>Attention</h3>
                {attention.length === 0 ? <p className="muted">Nothing currently requires attention.</p> :
                  <div className="attention-list">{attention.map((item, index) => {
                    const target = attentionTarget(item);
                    const content = <><span className={"severity " + String(item.severity).toLowerCase()}>{item.severity}</span><span className="attention-item-copy"><strong>{item.title}</strong><span>{item.message}</span></span>{target && <span className="attention-item-action" aria-hidden="true">Open <span>→</span></span>}</>;
                    return target
                      ? <button type="button" className="attention-item attention-item-clickable" key={item.type + index} onClick={() => { window.location.hash = target; }} aria-label={`${item.title}. ${item.message}. Open related record`}>{content}</button>
                      : <div className="attention-item" key={item.type + index}>{content}</div>;
                  })}</div>}
              </div>
            </div>
            <div className="subsection"><h3>Recent audit activity</h3>{auditRows.length === 0 ? <p className="muted">No audit records yet.</p> :
              <div className="table"><div className="row header"><span>Time</span><span>Action</span><span>Entity</span><span>Reason</span></div>
              {auditRows.slice(0, 8).map(row => <div className="row" key={row.id}><span>{formatAuditTimestamp(row.occurredAt, farm?.timezone)}</span><span>{formatAuditAction(row.action)}</span><span>{formatAuditEntity(row.entityType)}</span><span>{typeof row.reason === "string" && row.reason.trim() ? row.reason : "—"}</span></div>)}</div>}</div>
          </>
        ) : <p className="muted">Dashboard is loading…</p>}
      </section></PageSection>
    </div>
  );
}
