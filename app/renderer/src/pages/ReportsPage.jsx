import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
import { reportApi, productionTrendApi } from "../services/api.js";
// eslint-disable-next-line no-unused-vars
import ProductionTrendChart from "../components/ProductionTrendChart.jsx";
import { rememberLastBatchId } from "../utils/farmUx.js";
// eslint-disable-next-line no-unused-vars
import MetricHelp from "../components/MetricHelp.jsx";

export default function ReportsPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, farm, batches, selectedBatchId, setSelectedBatchId, farmReport, batchReport, reportAsOf, setReportAsOf, loadReport, loadBatchReport, exportCsv, exportPdf } = useContext(FarmManagerContext);

  return (
    <PageTemplate className="grid">
      <PageSection page="reports" tab="farm-report"><section id="reports" className="card full">
          <div className="section-head"><div><p className="eyebrow">REPORTING</p><h2>Farm reports</h2></div>
            <div className="inline-form"><label>As of <input aria-label="Report as-of date" type="date" value={reportAsOf} onChange={e => setReportAsOf(e.target.value)} /></label><button type="button" onClick={() => loadReport(reportAsOf)}>Refresh</button><button type="button" onClick={() => exportCsv(() => reportApi.farmCsv(reportAsOf), "farm-report.csv")}>Export CSV</button><button type="button" onClick={() => exportPdf(() => reportApi.farmPdf(reportAsOf), "farm-report.pdf")}>Export PDF</button></div>
          </div>
          {farmReport ? <>
            <div className="metric-grid">
              {[
                ["Total birds", formatCount(farmReport.dashboard?.totalBirds)],
                ["Mortality", formatCount(farmReport.dashboard?.mortality)],
                ["Feed types", formatCount(farmReport.feed?.length)],
                ["Expenses", formatMoney(farmReport.dashboard?.expensesMinor, farm.currency)],
                ["Revenue", formatMoney(farmReport.dashboard?.revenueMinor, farm.currency)],
                ["Profit", formatMoney(farmReport.dashboard?.profitMinor, farm.currency)],
                ["Egg batches", formatCount(farmReport.eggProduction?.length)],
                ["Broiler batches", formatCount(farmReport.broilerGrowth?.length)],
                ["Sales records", formatCount(farmReport.sales?.length)],
                ["Inventory items", formatCount(farmReport.inventory?.length)]
              ].map(([label,value]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong></div>)}
            </div>
            <p className="muted">As of {farmReport.asOf}. Water quantities use the farm display label: {farmReport.waterUnitLabel || "litres"}. All report metrics are assembled from the same calculation services used by the dashboards.</p>
          </> : <p className="muted">Report is loading…</p>}
        </section></PageSection>

      <PageSection page="reports" tab="batch-report"><section id="batch-reports" className="card full">
          <div className="section-head"><div><p className="eyebrow">REPORTING</p><h2>Batch reports</h2></div>
            <div className="inline-form">
              <select aria-label="Batch report" value={selectedBatchId} onChange={e => { setSelectedBatchId(e.target.value); rememberLastBatchId(e.target.value); }}>
                <option value="">Select a batch</option>{batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code} · {batch.type === "LAYER" ? "Layer" : batch.type === "BROILER" ? "Broiler" : "Unknown type"}</option>)}
              </select>
              <button type="button" onClick={() => loadBatchReport(selectedBatchId, reportAsOf)}>Refresh</button>
              <button type="button" disabled={!selectedBatchId} onClick={() => exportCsv(() => reportApi.batchCsv(selectedBatchId, reportAsOf), "batch-report.csv")}>Export CSV</button>
              <button type="button" disabled={!selectedBatchId} onClick={() => exportPdf(() => reportApi.batchPdf(selectedBatchId, reportAsOf), "batch-report.pdf")}>Export PDF</button>
            </div>
          </div>
          {batchReport ? <>
            <div className="metric-grid">
              {[
                ["Initial birds", batchReport.population?.initialBirds],
                ["Current birds", batchReport.population?.currentBirds],
                ["Mortality", batchReport.mortality],
                ["Feed", formatQuantity(batchReport.feed?.totalQuantityMilli)],
                ["Costs", formatMoney(batchReport.costs?.expensesMinor, farm.currency)],
                ["Revenue", formatMoney(batchReport.revenueMinor, farm.currency)],
                ["Profit", formatMoney(batchReport.profitMinor, farm.currency)],
                ["Health records", batchReport.health?.healthRecords?.length],
                ["Sales records", batchReport.sales?.length]
              ].map(([label,value]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong></div>)}
            </div>
            <div className="dashboard-columns">
              <div className="subsection"><h3>Production</h3>
                {batchReport.production?.type === "LAYER" ? <>
                  <div className="dashboard-list">
                    <div><span>Good eggs</span><strong>{batchReport.production.eggInventory?.goodCollected ?? "—"}</strong></div>
                    <div><span>Egg inventory</span><strong>{batchReport.production.eggInventory?.goodRemaining ?? "—"}</strong></div>
                    <div><span>Good egg rate</span><strong>{formatPercent(batchReport.production.eggQuality?.goodRatePercent)}</strong></div>
                  </div>
                </> : <>
                  <div className="dashboard-list">
                    <div><span>Average weight</span><strong>{formatKg(batchReport.production.growth?.currentAverageWeightKg)}</strong></div>
                    <div><span>Growth</span><strong>{formatKg(batchReport.production.growth?.liveWeightGainKg)}</strong></div>
                    <div><span>FCR <MetricHelp id="report-fcr-help" label="FCR formula">Feed conversion ratio (FCR) = converted feed consumed (kg) ÷ live-weight gain (kg) for this broiler batch as of the report date. The backend converts configured feed units to kilograms; non-kg feed is included when its kg-per-unit factor is configured. These are batch-to-date values, not a daily ratio. Lower FCR means less feed per kilogram of live-weight gain. If any consumed feed conversion is unknown, or live-weight gain is zero or negative, FCR is undefined and shown as —.</MetricHelp></span><strong>{formatMetric(batchReport.production.growth?.fcr)}</strong></div>
                  </div>
                </>}
              </div>
              <div className="subsection"><h3>Health</h3>
                <div className="dashboard-list">
                  <div><span>Health records</span><strong>{batchReport.health?.healthRecords?.length ?? 0}</strong></div>
                  <div><span>Drug records</span><strong>{batchReport.health?.drugs?.length ?? 0}</strong></div>
                  <div><span>Vaccinations</span><strong>{batchReport.health?.vaccinations?.length ?? 0}</strong></div>
                </div>
              </div>
            </div>
            <p className="muted">As of {batchReport.asOf}. Water quantities use the farm display label: {batchReport.waterUnitLabel || "litres"}. Population, feed, production, costs, revenue and profit reuse the dashboard calculation services.</p>
          </> : <p className="muted">{selectedBatchId ? "Batch report is loading…" : "Select a batch to inspect its report."}</p>}
          {selectedBatchId && batches.find(batch => batch.id === selectedBatchId)?.type === "LAYER" && (
            <ProductionTrendChart
              batchId={selectedBatchId}
              title="Layer HDP%"
              titleHelp={<MetricHelp id="report-hdp-chart-help" label="HDP chart formula">HDP% for each date = total eggs collected that date (good + cracked, in eggs) ÷ live layer birds on that date × 100. The backend calculates the historical live-bird count. Missing egg records are gaps, not zero.</MetricHelp>}
              description="Daily eggs collected (good + cracked) as a percentage of the historical live-bird population."
              unit="percent"
              loadTrend={productionTrendApi.layerHdp}
            />
          )}
          {selectedBatchId && batches.find(batch => batch.id === selectedBatchId)?.type === "BROILER" && (
            <ProductionTrendChart
              batchId={selectedBatchId}
              title="Broiler average weight"
              description="Daily average sampled bird weight in kilograms, calculated by the backend."
              unit="kg"
              loadTrend={productionTrendApi.broilerAverageWeight}
            />
          )}
        </section></PageSection>
    </PageTemplate>
  );
}
