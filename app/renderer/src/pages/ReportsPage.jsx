import { useContext, useEffect, useMemo, useState } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
import { reportApi, productionTrendApi } from "../services/api.js";
import { BarChart, Bar, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
// eslint-disable-next-line no-unused-vars
import DateRangeFilter from "../components/DateRangeFilter.jsx";
import { calculatePeriodProfitMinor } from "../utils/reportMetrics.js";
// eslint-disable-next-line no-unused-vars
import ProductionTrendChart from "../components/ProductionTrendChart.jsx";
import { rememberLastBatchId } from "../utils/farmUx.js";
// eslint-disable-next-line no-unused-vars
import EmptyState from "../components/EmptyState.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import MetricHelp from "../components/MetricHelp.jsx";

export default function ReportsPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, feedQuantity, farm, houses, batches, selectedBatchId, setSelectedBatchId, farmReport, batchReport, dateRange, setDateRange, applicationSettings, loadReport, loadBatchReport, exportCsv, exportPdf, reportLoading, batchReportLoading, dataLoadError, retryData } = useContext(FarmManagerContext);

  const farmPeriodProfitMinor = farmReport?.period
    ? calculatePeriodProfitMinor(farmReport.period.revenueMinor, farmReport.period.expensesMinor)
    : null;
  const batchPeriodProfitMinor = batchReport?.period
    ? calculatePeriodProfitMinor(batchReport.period.revenueMinor, batchReport.period.expensesMinor)
    : null;

  return (
    <PageTemplate className="grid pomas-page page-reports">
      <PageSection page="reports" tab="farm-report"><section id="reports" className="card full">
          <div className="section-head"><div><p className="eyebrow">REPORTING</p><h2>Farm reports</h2></div>
            <div className="report-range-actions"><DateRangeFilter value={dateRange} timezone={farm?.timezone} dateFormat={applicationSettings?.dateFormat} onChange={setDateRange} /><div className="inline-form"><button type="button" onClick={() => loadReport(dateRange.from, dateRange.to)}>Refresh</button><button type="button" onClick={() => exportCsv(() => reportApi.farmCsv(null, dateRange.from, dateRange.to, farmPeriodProfitMinor), "farm-report.csv")}>Export CSV</button><button type="button" onClick={() => exportPdf(() => reportApi.farmPdf(null, dateRange.from, dateRange.to, farmPeriodProfitMinor), "farm-report.pdf")}>Export PDF</button></div></div>
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
          </> : reportLoading ? <EmptyState variant="loading" heading="Loading farm report" compact /> : dataLoadError ? <EmptyState variant="error" description={dataLoadError} onRetry={retryData} compact /> : <EmptyState variant="no-records" heading="Farm report is not available" description="Refresh the report to retrieve the latest farm summary." compact />}
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
            <p className="muted">As of {batchReport.asOf}. Water quantities use the farm display label: {batchReport.waterUnitLabel || "litres"}. Population, feed, production, costs, revenue and profit reuse the dashboard calculation services.</p>
          </> : !selectedBatchId ? <EmptyState variant="not-applicable" heading="Select a batch" description="Choose a batch above to view its report." compact /> : (batchReportLoading || reportLoading) ? <EmptyState variant="loading" heading="Loading batch report" compact /> : dataLoadError ? <EmptyState variant="error" description={dataLoadError} onRetry={retryData} compact /> : <EmptyState variant="no-records" heading="Batch report is not available" description="Refresh the report to retrieve this batch summary." compact />}
          {selectedBatchId && batches.find(batch => batch.id === selectedBatchId)?.type === "LAYER" && (
            <ProductionTrendChart
              batchId={selectedBatchId}
              title="Layer HDP%"
              titleHelp={<MetricHelp id="report-hdp-chart-help" label="HDP chart formula">HDP% for each date = total eggs collected that date (good + cracked, in eggs) ÷ live layer birds on that date × 100. The backend calculates the historical live-bird count. Missing egg records are gaps, not zero.</MetricHelp>}
              description="Daily eggs collected (good + cracked) as a percentage of the historical live-bird population."
              unit="percent"
              loadTrend={productionTrendApi.layerHdp}
              dateRange={dateRange}
            />
          )}
          {selectedBatchId && ["BROILER", "TURKEY"].includes(batches.find(batch => batch.id === selectedBatchId)?.type) && (
            <ProductionTrendChart
              batchId={selectedBatchId}
              title="Broiler average weight"
              description="Daily average sampled bird weight in kilograms, calculated by the backend."
              unit="kg"
              loadTrend={productionTrendApi.broilerAverageWeight}
              dateRange={dateRange}
            />
          )}
        </section></PageSection>
      <PageSection page="reports" tab="batch-comparison">
        <BatchComparison
          batches={batches}
          houses={houses}
          dateRange={dateRange}
          setDateRange={setDateRange}
          farm={farm}
          applicationSettings={applicationSettings}
          formatMoney={formatMoney}
          formatKg={formatKg}
          formatMetric={formatMetric}
          formatCount={formatCount}
          formatPercent={formatPercent}
          feedQuantity={feedQuantity}
        />
      </PageSection>
    </PageTemplate>
  );
}


const COMPARISON_METRICS = [
  { key: "initialBirds", label: "Initial population", unit: "birds", kind: "count" },
  { key: "currentBirds", label: "Current population", unit: "birds at period end", kind: "count" },
  { key: "mortalityRatePercent", label: "Mortality rate", unit: "% of initial population in period", kind: "percent" },
  { key: "feedQuantityMilli", label: "Feed quantity", unit: "recorded feed units (units may differ)", kind: "quantity" },
  { key: "feedCostMinor", label: "Feed cost", unit: "__CURRENCY__", kind: "money" },
  { key: "averageWeightKg", label: "Average weight at period end", unit: "kg", kind: "kg", types: ["BROILER", "TURKEY"] },
  { key: "fcr", label: "FCR (batch-to-date)", unit: "kg feed / kg gain", kind: "metric", types: ["BROILER", "TURKEY"] },
  { key: "eggProduction", label: "Eggs collected", unit: "eggs in period", kind: "count", types: ["LAYER"] },
  { key: "revenueMinor", label: "Revenue", unit: "__CURRENCY__", kind: "money" },
  { key: "expensesMinor", label: "Expenses", unit: "__CURRENCY__", kind: "money" },
  { key: "profitMinor", label: "Profit", unit: "__CURRENCY__", kind: "money" },
];

export function BatchComparison({ batches, houses, dateRange, setDateRange, farm, applicationSettings, formatMoney, formatKg, formatMetric, formatCount, formatPercent, feedQuantity }) {
  const [selectedIds, setSelectedIds] = useState([]);
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [houseFilter, setHouseFilter] = useState("ALL");
  const [metricKey, setMetricKey] = useState("currentBirds");
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const filteredBatches = useMemo(() => batches.filter(batch =>
    (typeFilter === "ALL" || batch.type === typeFilter)
    && (houseFilter === "ALL" || batch.houseId === houseFilter)
  ), [batches, typeFilter, houseFilter]);
  const filteredIds = useMemo(() => selectedIds.filter(id => filteredBatches.some(batch => batch.id === id)), [selectedIds, filteredBatches]);
  const idsKey = filteredIds.join(",");
  const selectedReports = useMemo(() => reports.filter(report => filteredIds.includes(report.batch?.id)), [reports, filteredIds]);
  const selectedTypes = selectedReports.map(report => report.batch?.type);
  const allLayers = selectedReports.length > 0 && selectedTypes.every(type => type === "LAYER");
  const allMeatBirds = selectedReports.length > 0 && selectedTypes.every(type => type === "BROILER" || type === "TURKEY");
  const metricDefinitions = useMemo(() => COMPARISON_METRICS.filter(metric =>
    !metric.types || (metric.types.includes("LAYER") ? allLayers : allMeatBirds)
  ).map(metric => ({ ...metric, unit: metric.unit === "__CURRENCY__" ? farm.currency : metric.unit })), [allLayers, allMeatBirds, farm.currency]);
  const activeMetric = metricDefinitions.find(metric => metric.key === metricKey) || metricDefinitions[0];

  useEffect(() => {
    let current = true;
    if (filteredIds.length < 2 || !dateRange?.from || !dateRange?.to) {
      setReports([]);
      setError("");
      setLoading(false);
      return () => { current = false; };
    }
    setLoading(true);
    setError("");
    reportApi.compare(filteredIds, dateRange.from, dateRange.to).then(result => {
      if (current) setReports(result.reports || []);
    }).catch(err => {
      if (current) {
        setReports([]);
        setError(err?.message || "Batch comparison could not be loaded.");
      }
    }).finally(() => {
      if (current) setLoading(false);
    });
    return () => { current = false; };
  }, [idsKey, dateRange?.from, dateRange?.to]);

  const chartData = selectedReports.map(report => ({
    batch: report.batch?.code || "Batch",
    value: report.comparisonMetrics?.[activeMetric?.key] ?? null,
  }));
  const hiddenSelectedCount = selectedIds.length - filteredIds.length;

  function formatValue(metric, value) {
    if (value == null || !Number.isFinite(Number(value))) return "—";
    switch (metric.kind) {
      case "count": return formatCount(value);
      case "percent": return formatPercent(value);
      case "quantity": return feedQuantity(value);
      case "money": return formatMoney(value, farm.currency);
      case "kg": return formatKg(value);
      case "metric": return formatMetric(value);
      default: return String(value);
    }
  }

  function toggleBatch(id, checked) {
    setSelectedIds(current => checked
      ? (current.length >= 8 || current.includes(id) ? current : [...current, id])
      : current.filter(value => value !== id));
  }

  return (
    <section className="card full batch-comparison">
      <div className="section-head">
        <div><p className="eyebrow">FLOCK ANALYSIS</p><h2>Compare batch performance</h2></div>
        <DateRangeFilter value={dateRange} timezone={farm?.timezone} dateFormat={applicationSettings?.dateFormat} onChange={setDateRange} />
      </div>
      <p className="muted">Select 2–8 batches. Activity metrics use the same inclusive date range. Population and average weight are snapshots at the end date; FCR remains the backend's batch-to-date calculation.</p>

      <div className="inline-form comparison-filters">
        <label className="field"><span>Bird type</span><select aria-label="Filter batches by bird type" value={typeFilter} onChange={event => setTypeFilter(event.target.value)}>
          <option value="ALL">All types</option><option value="LAYER">Layers</option><option value="BROILER">Broilers</option><option value="TURKEY">Turkeys</option>
        </select></label>
        <label className="field"><span>House / pen</span><select aria-label="Filter batches by house" value={houseFilter} onChange={event => setHouseFilter(event.target.value)}>
          <option value="ALL">All houses</option>{houses.map(house => <option key={house.id} value={house.id}>{house.name} ({house.code})</option>)}
        </select></label>
        <span className="muted comparison-count">{filteredIds.length} selected for comparison · maximum 8</span>
      </div>

      <div className="comparison-selection" aria-label="Select batches to compare">
        {filteredBatches.length ? filteredBatches.map(batch => (
          <label className="comparison-option" key={batch.id}>
            <input type="checkbox" checked={selectedIds.includes(batch.id)} disabled={!selectedIds.includes(batch.id) && selectedIds.length >= 8} onChange={event => toggleBatch(batch.id, event.target.checked)} />
            <span><strong>{batch.code}</strong><small>{batch.type} · {batch.placementDate} · {houses.find(house => house.id === batch.houseId)?.name || "House not recorded"}</small></span>
          </label>
        )) : <p className="muted">No batches match these filters.</p>}
      </div>
      {hiddenSelectedCount > 0 && <p className="muted">{hiddenSelectedCount} selected batch(es) are temporarily excluded by the current filters.</p>}

      {selectedIds.length > 0 && <div className="comparison-chips" aria-label="Selected batches">
        {selectedIds.map(id => {
          const batch = batches.find(item => item.id === id);
          if (!batch) return null;
          return <span className="comparison-chip" key={id}>{batch.code}
            <button type="button" aria-label={"Remove " + batch.code} onClick={() => setSelectedIds(current => current.filter(value => value !== id))}>×</button>
          </span>;
        })}
        <button type="button" className="secondary-button" onClick={() => setSelectedIds([])}>Clear selection</button>
      </div>}

      {filteredIds.length < 2 ? <p className="muted" role="status">Select at least two batches that match the current filters to see a comparison.</p>
        : loading ? <p className="muted" role="status">Loading batch reports…</p>
        : error ? <p className="error" role="alert">{error}</p>
        : selectedReports.length < 2 ? <p className="muted">No comparison data is available for the selected batches.</p>
        : <>
          <div className="comparison-period-note">Activity period: <strong>{dateRange.from}</strong> to <strong>{dateRange.to}</strong>. Dates are inclusive. Feed quantity uses recorded feed units, which may differ between products. Egg production is shown only for layer-only comparisons; weight and FCR are shown only for meat-bird-only comparisons.</div>
          <div className="comparison-chart">
            <div className="section-head"><div><h3>Metric comparison</h3><p className="muted">One unit-consistent metric at a time.</p></div>
              <label className="field"><span>Chart metric</span><select aria-label="Comparison chart metric" value={activeMetric?.key || ""} onChange={event => setMetricKey(event.target.value)}>
                {metricDefinitions.map(metric => <option key={metric.key} value={metric.key}>{metric.label}</option>)}
              </select></label>
            </div>
            {activeMetric && <div role="img" aria-label={activeMetric.label + " comparison across selected batches"} style={{ width: "100%", height: 280 }}>
              <ResponsiveContainer>
                <BarChart data={chartData} margin={{ top: 12, right: 20, bottom: 12, left: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="batch" />
                  <YAxis />
                  <Tooltip formatter={value => formatValue(activeMetric, value)} />
                  <Bar dataKey="value" name={activeMetric.label} fill="var(--cyan)" />
                </BarChart>
              </ResponsiveContainer>
            </div>}
          </div>
          <div className="subsection comparison-table-wrap">
            <h3>Comparison table</h3>
            <div className="table-scroll" role="region" aria-label="Batch comparison table" tabIndex={0}>
              <table className="data-table comparison-table">
                <caption>Batch performance metrics for {dateRange.from} to {dateRange.to}</caption>
                <thead><tr><th scope="col">Metric and unit</th>{selectedReports.map(report => <th scope="col" key={report.batch.id}>{report.batch.code}<small>{report.batch.type} · placed {report.batch.placementDate}</small></th>)}</tr></thead>
                <tbody>{metricDefinitions.map(metric => <tr key={metric.key}>
                  <th scope="row">{metric.label}<small>{metric.unit}</small></th>
                  {selectedReports.map(report => <td key={report.batch.id}>{formatValue(metric, report.comparisonMetrics?.[metric.key])}</td>)}
                </tr>)}</tbody>
              </table>
            </div>
          </div>
        </>}
    </section>
  );
}
