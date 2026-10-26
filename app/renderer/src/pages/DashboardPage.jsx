import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
import { formatAuditAction, formatAuditEntity, formatAuditTimestamp } from "../utils/auditFormatting.js";

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
    dashboard,
    attention,
    auditRows,
    applicationSettings,
  } = useContext(FarmManagerContext);

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
        {dashboard ? (
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
                  <div className="attention-list">{attention.map((item, index) => <div className="attention-item" key={item.type + index}><span className={"severity " + String(item.severity).toLowerCase()}>{item.severity}</span><div><strong>{item.title}</strong><p>{item.message}</p></div></div>)}</div>}
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
