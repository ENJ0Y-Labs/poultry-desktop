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
import DateRangeFilter from "../components/DateRangeFilter.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import MetricHelp from "../components/MetricHelp.jsx";

export default function ReportsPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, feedQuantity, farm, batches, selectedBatchId, setSelectedBatchId, farmReport, batchReport, dateRange, setDateRange, loadReport, loadBatchReport, exportCsv, exportPdf } = useContext(FarmManagerContext);
  const farmPeriodProfitMinor = farmReport?.period
    ? Number(farmReport.period.revenueMinor || 0) - Number(farmReport.period.expensesMinor || 0)
    : farmReport?.dashboard?.profitMinor;
  const batchPeriodProfitMinor = batchReport?.period
    ? Number(batchReport.period.revenueMinor || 0) - Number(batchReport.period.expensesMinor || 0)
    : batchReport?.profitMinor;

  return (
    <PageTemplate className="grid">
      <PageSection page="reports" tab="farm-report"><section id="reports" className="card full">
          <div className="section-head"><div><p className="eyebrow">REPORTING</p><h2>Farm reports</h2></div>
            <div className="report-range-actions"><DateRangeFilter value={dateRange} timezone={farm?.timezone} onChange={setDateRange} /><div className="inline-form"><button type="button" onClick={() => loadReport(dateRange.from, dateRange.to)}>Refresh</button><button type="button" onClick={() => exportCsv(() => reportApi.farmCsv(null, dateRange.from, dateRange.to, farmPeriodProfitMinor), "farm-report.csv")}>Export CSV</button><button type="button" onClick={() => exportPdf(() => reportApi.farmPdf(null, dateRange.from, dateRange.to, farmPeriodProfitMinor), "farm-report.pdf")}>Export PDF</button></div></div>
          </div>
          {farmReport ? <>
            <div className="metric-grid">
              {[
                ["Total birds", formatCount(farmReport.dashboard?.totalBirds)],
                ["Mortality", formatCount(farmReport.period?.mortality ?? farmReport.dashboard?.mortality)],
                ["Feed types", formatCount(farmReport.feed?.length)],
                ["Expenses", formatMoney(farmReport.period?.expensesMinor ?? farmReport.dashboard?.expensesMinor, farm.currency)],
                ["Revenue", formatMoney(farmReport.period?.revenueMinor ?? farmReport.dashboard?.revenueMinor, farm.currency)],
                ["Profit", formatMoney(farmPeriodProfitMinor, farm.currency)],
                ["Egg batches", formatCount(farmReport.eggProduction?.length)],
                ["Broiler batches", formatCount(farmReport.broilerGrowth?.length)],
                ["Turkey batches", formatCount(farmReport.meatBirdGrowth?.filter(row => row.batch?.type === "TURKEY").length)],
                ["Sales records", formatCount(farmReport.period?.salesCount ?? farmReport.sales?.length)],
                ["Inventory items", formatCount(farmReport.inventory?.length)]
              ].map(([label,value]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong></div>)}
            </div>
            <p className="muted">Activity period: {farmReport.from || dateRange.from} to {farmReport.to || dateRange.to}. Bird population and feed inventory are current snapshots; activity totals include both selected boundary dates. Water quantities use {farmReport.waterUnitLabel || "litres"}.</p>
            <div className="subsection">
              <h3>Feed inventory by product</h3>
              <DataTable caption="Feed inventory by product" columns={[
                { key: "feedTypeName", label: "Feed product" },
                { key: "brand", label: "Brand", render: item => item.brand || "—" },
                { key: "packagingName", label: "Package", render: item => item.packagingName || "—" },
                { key: "purchasedQuantityMilli", label: "Purchased", render: item => `${feedQuantity(item.purchasedQuantityMilli)} ${item.unit}` },
                { key: "openingQuantityMilli", label: "Opening", render: item => `${feedQuantity(item.openingQuantityMilli)} ${item.unit}` },
                { key: "consumedQuantityMilli", label: "Used", render: item => `${feedQuantity(item.consumedQuantityMilli)} ${item.unit}` },
                { key: "remainingQuantityMilli", label: "Remaining", render: item => `${feedQuantity(item.remainingQuantityMilli)} ${item.unit}` },
                { key: "remainingCostMinor", label: "Remaining cost", render: item => item.remainingCostMinor == null ? "Unknown" : formatMoney(item.remainingCostMinor, farm.currency) },
              ]} rows={farmReport.feed || []} emptyMessage="No feed products recorded." />
            </div>
          </> : <p className="muted">Report is loading…</p>}
        </section></PageSection>

      <PageSection page="reports" tab="batch-report"><section id="batch-reports" className="card full">
          <div className="section-head"><div><p className="eyebrow">REPORTING</p><h2>Batch reports</h2></div>
            <div className="report-range-actions">
              <DateRangeFilter value={dateRange} timezone={farm?.timezone} onChange={setDateRange} />
              <div className="inline-form">
              <select aria-label="Batch report" value={selectedBatchId} onChange={e => { setSelectedBatchId(e.target.value); rememberLastBatchId(e.target.value); }}>
                <option value="">Select a batch</option>{batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code} · {batch.type === "LAYER" ? "Layer" : batch.type === "BROILER" ? "Broiler" : batch.type === "TURKEY" ? "Turkey" : "Unknown type"}</option>)}
              </select>
              <button type="button" onClick={() => loadBatchReport(selectedBatchId, dateRange.from, dateRange.to)}>Refresh</button>
              <button type="button" disabled={!selectedBatchId} onClick={() => exportCsv(() => reportApi.batchCsv(selectedBatchId, null, dateRange.from, dateRange.to, batchPeriodProfitMinor), "batch-report.csv")}>Export CSV</button>
              <button type="button" disabled={!selectedBatchId} onClick={() => exportPdf(() => reportApi.batchPdf(selectedBatchId, null, dateRange.from, dateRange.to, batchPeriodProfitMinor), "batch-report.pdf")}>Export PDF</button>
              </div>
            </div>
          </div>
          {batchReport ? <>
            <div className="metric-grid">
              {[
                ["Initial birds", batchReport.population?.initialBirds],
                ["Current birds", batchReport.population?.currentBirds],
                ["Mortality", batchReport.period?.mortality ?? batchReport.mortality],
                ["Feed", formatQuantity(batchReport.feed?.totalQuantityMilli)],
                ["Feed cost", batchReport.feed?.feedCostMinor == null ? "Unknown" : formatMoney(batchReport.feed.feedCostMinor, farm.currency)],
                ["Known feed cost", formatMoney(batchReport.feed?.knownFeedCostMinor, farm.currency)],
                ["Costs", formatMoney(batchReport.period?.expensesMinor ?? batchReport.costs?.expensesMinor, farm.currency)],
                ["Revenue", formatMoney(batchReport.period?.revenueMinor ?? batchReport.revenueMinor, farm.currency)],
                ["Profit", formatMoney(batchPeriodProfitMinor, farm.currency)],
                ["Health records", batchReport.period?.healthRecordCount ?? batchReport.health?.healthRecords?.length],
                ["Sales records", batchReport.period?.salesCount ?? batchReport.sales?.length]
              ].map(([label,value]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong></div>)}
            </div>
            <div className="dashboard-columns">
              <div className="subsection"><h3>Production</h3>
                {batchReport.production?.type === "LAYER" ? <>
                  <div className="dashboard-list">
                    <div><span>Good eggs collected (period)</span><strong>{batchReport.period?.eggsGood ?? "—"}</strong></div>
                    <div><span>Egg inventory</span><strong>{batchReport.production.eggInventory?.goodRemaining ?? "—"}</strong></div>
                    <div><span>Good egg rate</span><strong>{formatPercent(batchReport.production.eggQuality?.goodRatePercent)}</strong></div>
                  </div>
                </> : <>
                  <div className="dashboard-list">
                    <div><span>Average weight</span><strong>{formatKg(batchReport.production.growth?.currentAverageWeightKg)}</strong></div>
                    <div><span>Growth</span><strong>{formatKg(batchReport.production.growth?.liveWeightGainKg)}</strong></div>
                    <div><span>FCR <MetricHelp id="report-fcr-help" label="FCR formula">Feed conversion ratio (FCR) = converted feed consumed (kg) ÷ live-weight gain (kg) for this meat-production batch as of the report date. The backend converts configured feed units to kilograms; non-kg feed is included when its kg-per-unit factor is configured. These are batch-to-date values, not a daily ratio. Lower FCR means less feed per kilogram of live-weight gain. If any consumed feed conversion is unknown, or live-weight gain is zero or negative, FCR is undefined and shown as —.</MetricHelp></span><strong>{formatMetric(batchReport.production.growth?.fcr)}</strong></div>
                  </div>
                </>}
              </div>
              <div className="subsection"><h3>Health</h3>
                <div className="dashboard-list">
                  <div><span>Health records</span><strong>{batchReport.period?.healthRecordCount ?? batchReport.health?.healthRecords?.length ?? 0}</strong></div>
                  <div><span>Drug records</span><strong>{batchReport.period?.drugRecordCount ?? batchReport.health?.drugs?.length ?? 0}</strong></div>
                  <div><span>Vaccinations</span><strong>{batchReport.period?.vaccinationRecordCount ?? batchReport.health?.vaccinations?.length ?? 0}</strong></div>
                </div>
              </div>
            </div>
            <p className="muted">Activity period: {batchReport.from || dateRange.from} to {batchReport.to || dateRange.to}. Current population, feed cost, egg inventory, average weight and FCR remain snapshot or batch-to-date values. Water quantities use {batchReport.waterUnitLabel || "litres"}.</p>
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
          {selectedBatchId && ["BROILER", "TURKEY"].includes(batches.find(batch => batch.id === selectedBatchId)?.type) && (
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
