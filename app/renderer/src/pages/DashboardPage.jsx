import { useContext, useState } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
import { formatAuditAction, formatAuditEntity, formatAuditTimestamp } from "../utils/auditFormatting.js";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ContextDrawer, { DrawerDetails } from "../components/ContextDrawer.jsx";
import { attentionTarget } from "../utils/attentionNavigation.js";
import { calculatePeriodProfitMinor } from "../utils/reportMetrics.js";
// eslint-disable-next-line no-unused-vars
import DateRangeFilter from "../components/DateRangeFilter.jsx";
import { formatEggQuantity } from "../utils/eggFormatting.js";
// eslint-disable-next-line no-unused-vars
import GettingStartedChecklist from "../components/GettingStartedChecklist.jsx";
// eslint-disable-next-line no-unused-vars
import DashboardKpiCard from "../components/DashboardKpiCard.jsx";

function formatBusinessDate(value, pattern = "YYYY-MM-DD") {
  if (!value || typeof value !== "string") return "—";
  pattern = pattern || "YYYY-MM-DD";
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return value;

  const [, year, month, day] = match;
  const parts = { YYYY: year, MM: month, DD: day };
  return pattern.replace(/YYYY|MM|DD/g, token => parts[token]);
}

export default function DashboardPage() {
  const {
    formatMoney,
    formatQuantity,
    formatCount,
    crateSize,
    farm,
    houses,
    batches,
    feedTypes,
    feedInventory = [],
    dashboard,
    attention,
    auditRows,
    applicationSettings,
    dateRange,
    setDateRange,
    setFeedPurchaseModalSignal,
  } = useContext(FarmManagerContext);

  const [drawerRecord, setDrawerRecord] = useState(null);

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

  function openAuditDetails(row) {
    let changedFields = row.changedFields;
    if (typeof changedFields === "string") {
      try { changedFields = JSON.parse(changedFields); } catch { /* Preserve the recorded value. */ }
    }
    const changedFieldsLabel = Array.isArray(changedFields) ? changedFields.join(", ") : changedFields;
    const snapshotText = value => {
      if (value == null || value === "") return "";
      if (typeof value !== "string") return JSON.stringify(value, null, 2);
      try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
    };
    setDrawerRecord({
      title: formatAuditAction(row.action),
      description: "Immutable audit activity. The recorded values are displayed without changing audit history.",
      fields: [
        ["Entity ID", row.entityId],
        ["User ID", row.userId],
        ["Batch ID", row.batchId],
        ["Changed fields", changedFieldsLabel],
        ["Audit record ID", row.id],
      ].filter(([, value]) => value !== null && value !== undefined && value !== ""),
      before: snapshotText(row.before),
      after: snapshotText(row.after),
    });
  }



  const [checklistDismissed, setChecklistDismissed] = useState(
    () => localStorage.getItem("pomas:getting-started-dismissed") === "true"
  );
  function dismissChecklist() {
    localStorage.setItem("pomas:getting-started-dismissed", "true");
    setChecklistDismissed(true);
  }

  const periodProfitMinor = dashboard?.period
    ? calculatePeriodProfitMinor(dashboard.period.revenueMinor, dashboard.period.expensesMinor)
    : dashboard?.profitMinor;

  return (
    <PageTemplate className="grid pomas-page page-dashboard">
      <PageSection page="dashboard" tab="overview"><section id="dashboard" className="card full dashboard-page">
        <div className="section-head">
          <div><p className="eyebrow">OVERVIEW</p><h2>Farm dashboard</h2></div>
          <div className="dashboard-range-controls">
            <DateRangeFilter value={dateRange} timezone={farm?.timezone} dateFormat={applicationSettings?.dateFormat} onChange={setDateRange} />
            <p className="dashboard-as-of muted">
              <span>Dashboard as of</span>
              <small className="muted">Current snapshot</small>
              <strong>{dashboard ? formatBusinessDate(dashboard.asOf, applicationSettings?.dateFormat) : "Loading..."}</strong>
            </p>
          </div>
        </div>

        {dashboard ? isFirstRunEmpty && !checklistDismissed ? (
          <section className="first-run-empty" aria-labelledby="first-run-title">
            <p className="eyebrow">GETTING STARTED</p>
            <h3 id="first-run-title">Your farm is ready to be set up</h3>
            <p className="first-run-intro">These zeros are expected because no flock records have been entered yet. Start with your farm's physical setup, then add birds and record daily work. Nothing has been added automatically.</p>
            <button type="button" className="secondary-button" onClick={dismissChecklist}>Dismiss getting-started guidance</button>
            <ol className="first-run-steps">
              <li><span className="first-run-step-number">1</span><div><strong>Create a house or pen</strong><p>Add the place where your birds will be kept. A house is required before you can create a batch.</p><button type="button" onClick={() => navigateTo("flocks/houses")}>Open houses</button></div></li>
              <li><span className="first-run-step-number">2</span><div><strong>Open your first batch</strong><p>Record whether the flock is for layers or broilers, its placement date, and starting bird count.</p><button type="button" onClick={() => navigateTo("flocks/flocks")}>Open batches</button></div></li>
              <li><span className="first-run-step-number">3</span><div><strong>Set up and record feed</strong><p>Add a feed type, record a purchase, then record feed used by an active batch.</p><button type="button" onClick={() => navigateTo("feed-stock/feed")}>Configure feed</button></div></li>
              <li><span className="first-run-step-number">4</span><div><strong>Start daily records</strong><p>Record routine observations for your active batch. Mortality, water, health, and production can be entered in their own sections.</p><button type="button" onClick={() => navigateTo("daily-work/daily-record")}>Open daily record</button></div></li>
            </ol>
          </section>
        ) : (
          <>
            <GettingStartedChecklist houses={houses} batches={batches} feedTypes={feedTypes} feedInventory={feedInventory} onTaskAction={route => { if (route === "feed-stock/purchases") setFeedPurchaseModalSignal(value => value + 1); }} />
            <section className="dashboard-kpi-section" aria-label="Farm key performance indicators">
              <div className="dashboard-kpi-primary">
                <DashboardKpiCard label="Total birds" value={formatCount(dashboard.totalBirds)} primary href="#flocks/batch-dashboard" description="View flock populations and batch details" />
                <DashboardKpiCard label="Revenue" value={formatMoney(dashboard.revenueMinor, farm.currency)} primary href="#sales-money/sales" description="View recorded farm sales" />
                <DashboardKpiCard label="Profit" value={formatMoney(periodProfitMinor, farm.currency)} primary href="#reports/farm-report" description="Review the farm financial report" />
              </div>
              <div className="dashboard-kpi-secondary">
                <DashboardKpiCard label="Mortality" value={formatCount(dashboard.mortality)} unit="birds" href="#daily-work/mortality" description="Review mortality records" />
                <DashboardKpiCard label="Good eggs" value={formatEggQuantity(dashboard.eggsGood, crateSize)} href="#production/eggs" description="Review layer egg production" />
                <DashboardKpiCard label="Expenses" value={formatMoney(dashboard.expensesMinor, farm.currency)} href="#sales-money/expenses" description="Review recorded farm expenses" />
                <DashboardKpiCard label="Active batches" value={formatCount(dashboard.activeBatches)} href="#flocks/flocks" description="View active flocks" />
                <DashboardKpiCard label="Inventory alerts" value={formatCount(dashboard.inventoryAlerts)} href="#feed-stock/inventory" description="Review low and insufficient stock alerts" />
              </div>
              <div className="dashboard-kpi-detail-group" aria-label="Additional farm metrics">
                <p className="eyebrow">FLOCK & FACILITY BREAKDOWN</p>
                <div className="dashboard-kpi-detail-grid">
                  <DashboardKpiCard label="Estimated birds" value={formatCount(dashboard.estimatedBirds)} href="#flocks/batch-dashboard" description="View estimated flock populations" />
                  <DashboardKpiCard label="Estimated batches" value={formatCount(dashboard.estimatedBatches)} href="#flocks/flocks" description="View batches with estimated populations" />
                  <DashboardKpiCard label="Layer birds" value={formatCount(dashboard.layerBirds)} href="#flocks/batch-dashboard" description="View layer flock populations" />
                  <DashboardKpiCard label="Broiler birds" value={formatCount(dashboard.broilerBirds)} href="#flocks/batch-dashboard" description="View broiler flock populations" />
                  <DashboardKpiCard label="Turkey birds" value={formatCount(dashboard.turkeyBirds)} href="#flocks/batch-dashboard" description="View turkey flock populations" />
                  <DashboardKpiCard label="Total houses" value={formatCount(dashboard.totalHouses)} href="#flocks/houses" description="View farm houses and pens" />
                  <DashboardKpiCard label="Permanent houses" value={formatCount(dashboard.permanentHouses)} href="#flocks/houses" description="View permanent houses" />
                  <DashboardKpiCard label="Temporary houses" value={formatCount(dashboard.temporaryHouses)} href="#flocks/houses" description="View temporary houses" />
                  <DashboardKpiCard label="Occupied houses" value={formatCount(dashboard.occupiedHouses)} href="#flocks/houses" description="View occupied houses" />
                  <DashboardKpiCard label="Empty houses" value={formatCount(dashboard.emptyHouses)} href="#flocks/houses" description="View empty houses" />
                </div>
              </div>
            </section>
            <div className="dashboard-columns">
              <div className="subsection dashboard-feed-panel"><h3>Feed</h3><div className="dashboard-list">
                <div><span>Feed types</span><strong>{dashboard.feedSummary?.typeCount ?? 0}</strong></div>
                <div><span>Stock remaining</span><strong>{formatQuantity(dashboard.feedSummary?.remainingQuantityMilli)}</strong></div>
                <div><span>Stock value</span><strong>{dashboard.feedSummary?.remainingCostMinor == null ? "Unknown" : formatMoney(dashboard.feedSummary.remainingCostMinor, farm.currency)}</strong></div>
                <div><span>Consumed cost</span><strong>{dashboard.feedSummary?.consumedCostMinor == null ? "Unknown" : formatMoney(dashboard.feedSummary.consumedCostMinor, farm.currency)}</strong></div>
              </div></div>
              <div className="subsection dashboard-attention-panel"><h3>Attention</h3>
                {attention.length === 0 ? <p className="muted">Nothing currently requires attention.</p> :
                  <div className="attention-list">{attention.map((item, index) => {
                    const target = attentionTarget(item);
                    const contextLabel = item.batchCode
                      ? `${item.batchCode}${item.houseName ? ` · ${item.houseName}` : ""}`
                      : item.item || "";
                    const content = <><span className={"severity " + String(item.severity).toLowerCase()}>{item.severity}</span><span className="attention-item-copy"><strong>{item.title}</strong><span>{item.message}</span>{(item.affectedMetric || contextLabel) && <span className="attention-item-meta">{[item.affectedMetric, contextLabel].filter(Boolean).join(" · ")}</span>}{item.detectedAt && <span className="attention-item-meta">Detected: {item.detectedAt}</span>}{item.ruleDescription && <span className="attention-item-rule">{item.ruleDescription}</span>}{item.thresholdContext && <span className="attention-item-rule">Threshold: {item.thresholdContext}</span>}{item.suggestedAction && <span className="attention-item-action-copy">Suggested action: {item.suggestedAction}</span>}</span>{target && <span className="attention-item-action" aria-hidden="true">Open <span>→</span></span>}</>;
                    return target
                      ? <button type="button" className="attention-item attention-item-clickable" key={item.type + index} onClick={() => { window.location.hash = target; }} aria-label={`${item.title}. ${item.message}. ${item.affectedMetric || ""}. ${item.detectedAt || ""}. ${item.ruleDescription || ""}. ${item.thresholdContext || ""}. ${item.suggestedAction || ""}. Open related record`}>{content}</button>
                      : <div className="attention-item" key={item.type + index}>{content}</div>;
                  })}</div>}
              </div>
            </div>
            <div className="subsection"><h3>Recent audit activity</h3>{auditRows.length === 0 ? <p className="muted">No audit records yet.</p> :
              <DataTable caption="Recent audit activity" columns={[
                { key: "occurredAt", label: "Time", render: row => formatAuditTimestamp(row.occurredAt, farm?.timezone) },
                { key: "action", label: "Action", render: row => formatAuditAction(row.action) },
                { key: "entityType", label: "Entity", render: row => formatAuditEntity(row.entityType) },
                { key: "reason", label: "Reason", render: row => typeof row.reason === "string" && row.reason.trim() ? row.reason : "—" },
                { key: "details", label: "Details", render: row => <button type="button" className="secondary-button" onClick={() => openAuditDetails(row)}>View</button> },
              ]} rows={auditRows.slice(0, 8)} emptyMessage="No audit records yet." />}</div>
          </>
        ) : <p className="muted">Dashboard is loading…</p>}
      </section></PageSection>
      <ContextDrawer open={Boolean(drawerRecord)} onClose={() => setDrawerRecord(null)} title={drawerRecord?.title || "Audit details"} description={drawerRecord?.description} size="large">
        {drawerRecord && <DrawerDetails fields={drawerRecord.fields} />}
        {drawerRecord?.before && <section className="subsection">
          <h3>Before</h3>
          <pre className="pomas-audit-snapshot">{drawerRecord.before}</pre>
        </section>}
        {drawerRecord?.after && <section className="subsection">
          <h3>After</h3>
          <pre className="pomas-audit-snapshot">{drawerRecord.after}</pre>
        </section>}
      </ContextDrawer>
    </PageTemplate>
  );
}
