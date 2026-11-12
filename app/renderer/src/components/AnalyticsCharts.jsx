import { useEffect, useMemo, useState } from "react";
/* eslint-disable no-unused-vars -- Recharts elements are rendered in JSX. */
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis,
} from "recharts";
/* eslint-enable no-unused-vars */
import { dailyApi, reportApi } from "../services/api.js";
import { createDateRange, isValidDateRange } from "../utils/dateRange.js";
// eslint-disable-next-line no-unused-vars
import EmptyState from "./EmptyState.jsx";
// eslint-disable-next-line no-unused-vars
import DateRangeFilter from "./DateRangeFilter.jsx";

const CHART_COLORS = ["var(--amber)", "var(--cyan)", "var(--green)", "var(--red)", "#8b7cf6", "#e58ab8"];

// eslint-disable-next-line no-unused-vars -- ChartPanel renders children in JSX.
function ChartPanel({ title, description, empty, error, controls, children }) {
  return <section className="subsection analytics-chart-panel">
    <div className="section-head"><div><h3>{title}</h3><p className="muted">{description}</p></div></div>
    {controls}
    {error ? <EmptyState variant="error" description={error} compact /> : empty ? <EmptyState variant="no-records" heading="No chart data" description={empty} compact /> :
      <div className="analytics-chart">{children}</div>}
  </section>;
}

// eslint-disable-next-line no-unused-vars -- Recharts reads the tooltip props.
function ChartTooltip({ active, payload, label, formatValue = value => value }) {
  if (!active || !payload?.length) return null;
  return <div className="analytics-tooltip"><strong>{label}</strong>{payload.map(row =>
    <div key={row.dataKey}><span style={{ color: row.color }}>{row.name || row.dataKey}</span><b>{formatValue(row.value, row)}</b></div>
  )}</div>;
}

export function FinancialAnalytics({ sales = [], expenses = [], dateRange, setDateRange, timezone, dateFormat, showDateFilter = false, batches = [], houses = [], formatMoney, currency = "NGN" }) {
  const [selectedHouseId, setSelectedHouseId] = useState("");
  const [selectedBatchId, setSelectedBatchId] = useState("");
  const visibleBatches = batches.filter(batch => !selectedHouseId || batch.houseId === selectedHouseId);
  const visibleSales = sales.filter(row => (!selectedBatchId || row.batchId === selectedBatchId)
    && (!selectedHouseId || batches.some(batch => batch.id === row.batchId && batch.houseId === selectedHouseId)));
  const visibleExpenses = expenses.filter(row => (!selectedBatchId || row.batchId === selectedBatchId)
    && (!selectedHouseId || batches.some(batch => batch.id === row.batchId && batch.houseId === selectedHouseId)));
  const data = useMemo(() => {
    const totals = new Map();
    const add = (date, key, amount) => {
      if (!date || (dateRange?.from && date < dateRange.from) || (dateRange?.to && date > dateRange.to)) return;
      const row = totals.get(date) || { date, revenueMinor: 0, expenseMinor: 0 };
      row[key] += Number(amount || 0);
      totals.set(date, row);
    };
    visibleSales.forEach(row => add(row.saleDate, "revenueMinor", row.totalAmountMinor));
    visibleExpenses.forEach(row => add(row.occurredDate, "expenseMinor", row.amountMinor));
    return [...totals.values()].sort((a, b) => a.date.localeCompare(b.date)).map(row => ({
      ...row,
      netAfterExpensesMinor: row.revenueMinor - row.expenseMinor,
    }));
  }, [visibleSales, visibleExpenses, dateRange?.from, dateRange?.to]);
  const byCategory = useMemo(() => {
    const totals = new Map();
    visibleExpenses.forEach(row => {
      if ((dateRange?.from && row.occurredDate < dateRange.from) || (dateRange?.to && row.occurredDate > dateRange.to)) return;
      const category = row.category || "Uncategorized";
      totals.set(category, (totals.get(category) || 0) + Number(row.amountMinor || 0));
    });
    return [...totals].map(([name, value]) => ({ name, value }));
  }, [visibleExpenses, dateRange?.from, dateRange?.to]);
  const money = value => formatMoney ? formatMoney(value, currency) : `${currency} ${(Number(value || 0) / 100).toFixed(2)}`;

  return <>
    {showDateFilter && <div className="analytics-range-filter"><DateRangeFilter value={dateRange} timezone={timezone} dateFormat={dateFormat} onChange={setDateRange} /></div>}
    <div className="analytics-chart-filter-row analytics-context-filters">
      {houses.length > 0 && <label className="analytics-chart-filter">House / pen<select aria-label="Financial chart house" value={selectedHouseId} onChange={event => { setSelectedHouseId(event.target.value); setSelectedBatchId(""); }}><option value="">All houses</option>{houses.filter(house => house.status !== "ARCHIVED").map(house => <option key={house.id} value={house.id}>{house.name}</option>)}</select></label>}
      {batches.length > 0 && <label className="analytics-chart-filter">Batch<select aria-label="Financial chart batch" value={selectedBatchId} onChange={event => setSelectedBatchId(event.target.value)}><option value="">All batches</option>{visibleBatches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}</select></label>}
      {(selectedHouseId || selectedBatchId) && <span className="muted">Farm-level expenses are excluded from batch or house comparisons.</span>}
    </div>
    <div className="analytics-chart-grid">
    <ChartPanel title="Revenue vs expenses" description="Daily posted sales and expenses in the selected period." empty={!data.length && "No sales or expenses were recorded in this period."}>
      <ResponsiveContainer width="100%" height={250}><BarChart data={data} margin={{ top: 8, right: 12, bottom: 8, left: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="date" minTickGap={26} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis tick={{ fontSize: 10, fill: "var(--muted)" }} tickFormatter={value => money(value)} width={88} />
        <Tooltip content={<ChartTooltip formatValue={money} />} /><Legend /><Bar dataKey="revenueMinor" name="Revenue" fill={CHART_COLORS[0]} radius={[3, 3, 0, 0]} /><Bar dataKey="expenseMinor" name="Expenses" fill={CHART_COLORS[1]} radius={[3, 3, 0, 0]} />
      </BarChart></ResponsiveContainer>
    </ChartPanel>
    <ChartPanel title="Expense breakdown" description="Posted expenses grouped by the recorded category." empty={!byCategory.length && "No expenses were recorded in this period."}>
      <ResponsiveContainer width="100%" height={250}><PieChart><Pie data={byCategory} dataKey="value" nameKey="name" innerRadius={58} outerRadius={88} paddingAngle={2}>
        {byCategory.map((entry, index) => <Cell key={entry.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />)}
      </Pie><Tooltip content={<ChartTooltip formatValue={money} />} /><Legend /></PieChart></ResponsiveContainer>
    </ChartPanel>
    <ChartPanel title="Revenue less expenses" description="Daily sales less posted expenses; this activity view does not allocate bird costs. Values use integer minor currency units." empty={!data.length && "No financial activity was recorded in this period."}>
      <ResponsiveContainer width="100%" height={250}><LineChart data={data} margin={{ top: 8, right: 12, bottom: 8, left: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="date" minTickGap={26} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis tick={{ fontSize: 10, fill: "var(--muted)" }} tickFormatter={value => money(value)} width={88} />
        <Tooltip content={<ChartTooltip formatValue={money} />} /><Line type="monotone" dataKey="netAfterExpensesMinor" name="Revenue less expenses" stroke={CHART_COLORS[2]} strokeWidth={2} dot={false} activeDot={{ r: 4 }} isAnimationActive={false} />
      </LineChart></ResponsiveContainer>
    </ChartPanel>
    </div>
  </>;
}

export function FlockAnalytics({ populationRows = [], houses = [], formatCount = value => String(value ?? 0) }) {
  const batchData = useMemo(() => populationRows.filter(row => row?.batch && row?.population).map(row => ({
    name: row.batch.code,
    live: row.population.currentBirds,
    mortality: row.population.mortality,
    culling: row.population.culling,
    sold: row.population.sold,
    transfersIn: row.population.transfersIn,
    transfersOut: row.population.transfersOut,
    adjustmentNet: Number(row.population.currentBirds || 0) - (Number(row.population.initialBirds || 0)
      - Number(row.population.mortality || 0) - Number(row.population.culling || 0) - Number(row.population.sold || 0)
      + Number(row.population.transfersIn || 0) - Number(row.population.transfersOut || 0)),
  })), [populationRows]);
  const houseData = useMemo(() => houses.filter(house => house.status !== "ARCHIVED").map(house => ({
    name: house.name,
    birds: populationRows.filter(row => row.batch?.houseId === house.id).reduce((sum, row) => sum + Number(row.population?.currentBirds || 0), 0),
  })).sort((a, b) => b.birds - a.birds), [houses, populationRows]);
  return <div className="analytics-chart-grid">
    <ChartPanel title="Batch population and outcomes" description="Current birds, cumulative outcomes, transfers and the reconciled net adjustment. Series are grouped, not stacked, because event history does not form a simple composition." empty={!batchData.length && "No batch population records are available."}>
      <ResponsiveContainer width="100%" height={260}><BarChart data={batchData} margin={{ top: 8, right: 12, bottom: 8, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="name" minTickGap={14} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip formatValue={formatCount} />} /><Legend />
        <Bar dataKey="live" name="Live birds" fill={CHART_COLORS[0]} /><Bar dataKey="mortality" name="Mortality" fill={CHART_COLORS[3]} /><Bar dataKey="culling" name="Culling" fill={CHART_COLORS[1]} /><Bar dataKey="sold" name="Sold" fill={CHART_COLORS[2]} /><Bar dataKey="transfersIn" name="Transfers in" fill={CHART_COLORS[4]} /><Bar dataKey="transfersOut" name="Transfers out" fill={CHART_COLORS[5]} /><Bar dataKey="adjustmentNet" name="Net count adjustment" fill="#9aa4af" />
      </BarChart></ResponsiveContainer>
    </ChartPanel>
    <ChartPanel title="House comparison" description="Current live-bird population assigned to each house or pen." empty={!houseData.length && "No houses are available to compare."}>
      <ResponsiveContainer width="100%" height={260}><BarChart data={houseData} layout="vertical" margin={{ top: 8, right: 18, bottom: 8, left: 12 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis type="number" tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis type="category" dataKey="name" width={90} tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip formatValue={formatCount} />} /><Bar dataKey="birds" name="Live birds" fill={CHART_COLORS[0]} radius={[0, 4, 4, 0]} />
      </BarChart></ResponsiveContainer>
    </ChartPanel>
  </div>;
}

export function InventoryAnalytics({ items = [], movements = [], selectedItemId, dateRange, setDateRange, timezone, dateFormat }) {
  const stock = useMemo(() => items.filter(item => item.status !== "ARCHIVED").map(item => ({
    name: item.name,
    quantity: Number(item.quantityOnHand || 0),
    unit: item.unit,
  })).sort((a, b) => b.quantity - a.quantity), [items]);
  const [stockUnit, setStockUnit] = useState("");
  const availableUnits = [...new Set(stock.map(item => item.unit).filter(Boolean))];
  const selectedStockUnit = availableUnits.includes(stockUnit) ? stockUnit : availableUnits[0] || "";
  const visibleStock = stock.filter(item => item.unit === selectedStockUnit);
  const movementData = useMemo(() => {
    const grouped = new Map();
    const itemMovements = movements.filter(row => row.inventoryItemId === selectedItemId);
    for (const row of itemMovements) {
      const point = grouped.get(row.movementDate) || { date: row.movementDate, received: 0, issued: 0, waste: 0, adjustment: 0 };
      const quantity = Number(row.quantity || 0);
      if (row.movementType === "RECEIVE") { point.received += quantity; point.net = (point.net || 0) + quantity; }
      else if (row.movementType === "ISSUE") { point.issued += quantity; point.net = (point.net || 0) - quantity; }
      else if (row.movementType === "WASTE") { point.waste += quantity; point.net = (point.net || 0) - quantity; }
      else { const adjustment = row.movementType === "ADJUST_IN" ? quantity : -quantity; point.adjustment += adjustment; point.net = (point.net || 0) + adjustment; }
      grouped.set(row.movementDate, point);
    }
    const ordered = [...grouped.values()].sort((a, b) => a.date.localeCompare(b.date));
    let netAfterDate = ordered.reduce((total, row) => total + row.net, 0);
    const currentOnHand = Number(items.find(item => item.id === selectedItemId)?.quantityOnHand || 0);
    return ordered.map(row => {
      netAfterDate -= row.net;
      return { ...row, stockOnHand: currentOnHand - netAfterDate };
    }).filter(row => (!dateRange?.from || row.date >= dateRange.from) && (!dateRange?.to || row.date <= dateRange.to));
  }, [movements, items, selectedItemId, dateRange?.from, dateRange?.to]);
  return <>
    <div className="analytics-range-filter"><DateRangeFilter value={dateRange} timezone={timezone} dateFormat={dateFormat} onChange={setDateRange} /></div>
    <div className="analytics-chart-grid">
    <ChartPanel title="Stock levels" description={`Current calculated quantity on hand for active items measured in ${selectedStockUnit || "the selected unit"}.`} empty={!visibleStock.length && "No active inventory items are recorded in this unit."} controls={availableUnits.length > 1 && <label className="analytics-chart-filter">Unit<select aria-label="Stock chart unit" value={selectedStockUnit} onChange={event => setStockUnit(event.target.value)}>{availableUnits.map(unit => <option key={unit}>{unit}</option>)}</select></label>}>
      <ResponsiveContainer width="100%" height={260}><BarChart data={visibleStock} layout="vertical" margin={{ top: 8, right: 18, bottom: 8, left: 12 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis type="number" tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis type="category" dataKey="name" width={110} tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip formatValue={value => `${value} ${selectedStockUnit}`} />} /><Bar dataKey="quantity" name="On hand" fill={CHART_COLORS[0]} radius={[0, 4, 4, 0]} />
      </BarChart></ResponsiveContainer>
    </ChartPanel>
    <ChartPanel title="Stock movement" description="Ledger-derived remaining stock with recorded daily receipts, issues and waste for the selected item." empty={!movementData.length && "Select an item with movements in this period to view stock activity."}>
      <ResponsiveContainer width="100%" height={260}><AreaChart data={movementData} margin={{ top: 8, right: 12, bottom: 8, left: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="date" minTickGap={24} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip />} /><Legend />
        <Area type="stepAfter" dataKey="stockOnHand" name={`Remaining (${selectedStockUnit})`} stroke={CHART_COLORS[0]} fill={CHART_COLORS[0]} fillOpacity={0.2} isAnimationActive={false} /><Line type="monotone" dataKey="received" name={`Received (${selectedStockUnit})`} stroke={CHART_COLORS[2]} dot={false} isAnimationActive={false} /><Line type="monotone" dataKey="issued" name={`Issued (${selectedStockUnit})`} stroke={CHART_COLORS[1]} dot={false} isAnimationActive={false} /><Line type="monotone" dataKey="waste" name={`Waste (${selectedStockUnit})`} stroke={CHART_COLORS[3]} dot={false} isAnimationActive={false} />
      </AreaChart></ResponsiveContainer>
    </ChartPanel>
    </div>
  </>;
}

export function ProductionDistributionCharts({ eggCollections = [], weights = [], batch, dateRange }) {
  const eggs = useMemo(() => {
    const daily = new Map();
    eggCollections.forEach(row => {
      if ((dateRange?.from && row.recordDate < dateRange.from) || (dateRange?.to && row.recordDate > dateRange.to)) return;
      const point = daily.get(row.recordDate) || { date: row.recordDate, good: 0, cracked: 0 };
      point.good += Number(row.good || 0);
      point.cracked += Number(row.cracked || 0);
      daily.set(row.recordDate, point);
    });
    return [...daily.values()].sort((a, b) => a.date.localeCompare(b.date));
  }, [eggCollections, dateRange?.from, dateRange?.to]);
  const weightData = useMemo(() => weights.filter(row => row.averageWeightKg != null
    && (!dateRange?.from || row.recordDate >= dateRange.from) && (!dateRange?.to || row.recordDate <= dateRange.to))
    .map(row => ({ ...row, ageDays: Math.floor((Date.parse(`${row.recordDate}T00:00:00Z`) - Date.parse(`${batch?.placementDate}T00:00:00Z`)) / 86400000) }))
    .sort((a, b) => a.ageDays - b.ageDays), [weights, batch?.placementDate, dateRange?.from, dateRange?.to]);
  const distribution = useMemo(() => {
    if (!weightData.length) return [];
    const max = Math.max(...weightData.map(row => Number(row.averageWeightKg)));
    const binSize = Math.max(0.1, Math.ceil(max * 10 / 5) / 10);
    const bins = Array.from({ length: 5 }, (_, index) => ({ range: `${(index * binSize).toFixed(1)}–${((index + 1) * binSize).toFixed(1)} kg`, records: 0 }));
    weightData.forEach(row => { const index = Math.min(4, Math.floor(Number(row.averageWeightKg) / binSize)); bins[index].records += 1; });
    return bins;
  }, [weightData]);
  return <div className="analytics-chart-grid">
    {batch?.type === "LAYER" && <ChartPanel title="Egg quality by day" description="Recorded good and cracked eggs; the current data model does not record egg sizes or grades." empty={!eggs.length && "No egg collection records exist for this batch in the selected period."}>
      <ResponsiveContainer width="100%" height={250}><BarChart data={eggs} margin={{ top: 8, right: 12, bottom: 8, left: 4 }}><CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="date" minTickGap={24} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip />} /><Legend /><Bar dataKey="good" name="Good eggs" fill={CHART_COLORS[2]} /><Bar dataKey="cracked" name="Cracked eggs" fill={CHART_COLORS[3]} /></BarChart></ResponsiveContainer>
    </ChartPanel>}
    {batch?.type !== "LAYER" && <>
    <ChartPanel title="Weight by bird age" description="Scatter of recorded batch age against the backend average sample weight." empty={!weightData.length && "No weight records exist for this batch in the selected period."}>
      <ResponsiveContainer width="100%" height={250}><ScatterChart margin={{ top: 8, right: 14, bottom: 8, left: 8 }}><CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis type="number" dataKey="ageDays" name="Age" unit=" days" tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis type="number" dataKey="averageWeightKg" name="Average weight" unit=" kg" tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip cursor={{ strokeDasharray: "3 3" }} /><Scatter data={weightData} name="Recorded samples" fill={CHART_COLORS[0]} /></ScatterChart></ResponsiveContainer>
    </ChartPanel>
    <ChartPanel title="Recorded averages by weight band" description="Weight records grouped by their recorded average; individual bird weights are not stored." empty={!distribution.length && "No weight records exist for this batch in the selected period."}>
      <ResponsiveContainer width="100%" height={250}><BarChart data={distribution} margin={{ top: 8, right: 12, bottom: 8, left: 4 }}><CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="range" tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis allowDecimals={false} tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip />} /><Bar dataKey="records" name="Weight records" fill={CHART_COLORS[4]} /></BarChart></ResponsiveContainer>
    </ChartPanel>
    </>}
  </div>;
}

export function FarmDistributionChart({ dashboard, populationRows = [], batches = [], houses = [] }) {
  const [mode, setMode] = useState("type");
  const typeData = [
    { name: "Layers", value: Number(dashboard?.layerBirds || 0) },
    { name: "Broilers", value: Number(dashboard?.broilerBirds || 0) },
    { name: "Turkeys", value: Number(dashboard?.turkeyBirds || 0) },
  ].filter(row => row.value > 0);
  const data = mode === "type" ? typeData : mode === "house"
    ? houses.map(house => ({ name: house.name, value: populationRows.filter(row => row.batch?.houseId === house.id).reduce((total, row) => total + Number(row.population?.currentBirds || 0), 0) })).filter(row => row.value > 0)
    : batches.map(batch => ({ name: batch.code, value: Number(populationRows.find(row => row.batch?.id === batch.id)?.population?.currentBirds || 0) })).filter(row => row.value > 0);
  return <ChartPanel title="Flock distribution" description={`Current live birds grouped by ${mode === "type" ? "poultry type" : mode === "house" ? "house or pen" : "batch"}.`} empty={!data.length && "No live bird population is available to distribute."}>
    <label className="analytics-chart-filter">Group by<select aria-label="Flock distribution grouping" value={mode} onChange={event => setMode(event.target.value)}><option value="type">Poultry type</option><option value="house">House / pen</option><option value="batch">Batch</option></select></label>
    <ResponsiveContainer width="100%" height={250}><PieChart><Pie data={data} dataKey="value" nameKey="name" innerRadius={58} outerRadius={88} paddingAngle={2}>{data.map((row, index) => <Cell key={row.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />)}</Pie><Tooltip content={<ChartTooltip />} /><Legend /></PieChart></ResponsiveContainer>
  </ChartPanel>;
}

export function DashboardAnalyticsCharts({ farm, dashboard, batches = [], houses = [] }) {
  const [preset, setPreset] = useState("LAST_30_DAYS");
  const [range, setRange] = useState(() => createDateRange("LAST_30_DAYS", farm?.timezone));
  const [metric, setMetric] = useState("revenueMinor");
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!farm?.timezone || preset === "CUSTOM") return;
    setRange(createDateRange(preset, farm.timezone));
  }, [farm?.timezone, preset]);

  useEffect(() => {
    if (!isValidDateRange(range)) {
      setReport(null);
      setLoading(false);
      setError("Enter a valid date range.");
      return undefined;
    }
    const days = Math.floor((Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86400000) + 1;
    if (days > 366) {
      setReport(null);
      setLoading(false);
      setError("Choose a chart period of 366 days or fewer.");
      return;
    }
    let current = true;
    setLoading(true);
    setError("");
    reportApi.farm(null, range.from, range.to)
      .then(value => { if (current) setReport(value); })
      .catch(err => { if (current) { setReport(null); setError(err.message || "Could not load chart data."); } })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [range.from, range.to]);

  const data = useMemo(() => {
    const rows = new Map();
    const add = (date, key, value) => {
      const point = rows.get(date) || { date, revenueMinor: 0, expensesMinor: 0 };
      point[key] += Number(value || 0);
      rows.set(date, point);
    };
    report?.sales?.forEach(row => add(row.saleDate, "revenueMinor", row.totalAmountMinor));
    report?.expenses?.forEach(row => add(row.occurredDate, "expensesMinor", row.amountMinor));
    return [...rows.values()].sort((a, b) => a.date.localeCompare(b.date)).map(row => ({
      ...row,
      netAfterExpensesMinor: row.revenueMinor - row.expensesMinor,
    }));
  }, [report]);
  const selectedMetric = metric === "expensesMinor" ? "expensesMinor" : metric === "netAfterExpensesMinor" ? "netAfterExpensesMinor" : "revenueMinor";
  const metricLabel = selectedMetric === "expensesMinor" ? "Expenses" : selectedMetric === "netAfterExpensesMinor" ? "Revenue less expenses" : "Revenue";
  const money = value => `${farm?.currency || "NGN"} ${(Number(value || 0) / 100).toFixed(2)}`;
  const populationRows = report?.birdPopulation || [];

  return <div className="analytics-chart-grid dashboard-analytics-grid">
    <ChartPanel title="Farm performance" description={`${range.from} to ${range.to} · recorded sales and expenses`} error={error} empty={!loading && !data.length && "No sales or expense records exist in this period."} controls={<div className="analytics-chart-filter-row">
        <label className="analytics-chart-filter">Period<select aria-label="Farm performance period" value={preset} onChange={event => setPreset(event.target.value)}><option value="LAST_7_DAYS">Last 7 days</option><option value="LAST_30_DAYS">Last 30 days</option><option value="LAST_90_DAYS">Last 90 days</option><option value="CUSTOM">Custom</option></select></label>
        {preset === "CUSTOM" && <><label className="analytics-chart-filter">From<input aria-label="Farm performance from" type="date" value={range.from} onChange={event => setRange(current => ({ ...current, from: event.target.value }))} /></label><label className="analytics-chart-filter">To<input aria-label="Farm performance to" type="date" value={range.to} onChange={event => setRange(current => ({ ...current, to: event.target.value }))} /></label></>}
        <label className="analytics-chart-filter">Dataset<select aria-label="Farm performance dataset" value={metric} onChange={event => setMetric(event.target.value)}><option value="revenueMinor">Revenue</option><option value="expensesMinor">Expenses</option><option value="netAfterExpensesMinor">Revenue less expenses</option></select></label>
      </div>}>
      {loading ? <p className="muted">Loading chart data…</p> : error ? <p className="error" role="alert">{error}</p> : data.length > 0 && <div className="analytics-chart"><ResponsiveContainer width="100%" height={250}><LineChart data={data} margin={{ top: 8, right: 12, bottom: 8, left: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="date" minTickGap={26} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis tick={{ fontSize: 10, fill: "var(--muted)" }} tickFormatter={value => money(value)} width={88} /><Tooltip content={<ChartTooltip formatValue={money} />} /><Line type="monotone" dataKey={selectedMetric} name={metricLabel} stroke={CHART_COLORS[0]} strokeWidth={2} dot={false} activeDot={{ r: 4 }} isAnimationActive={false} />
      </LineChart></ResponsiveContainer></div>}
    </ChartPanel>
    <FarmDistributionChart dashboard={dashboard} populationRows={populationRows} batches={batches} houses={houses} />
  </div>;
}

export function DailyOperationsAnalytics({ batches = [], initialBatchId, dateRange, setDateRange, timezone, dateFormat }) {
  const [selectedIds, setSelectedIds] = useState([]);
  const [feedBatchId, setFeedBatchId] = useState(initialBatchId || "");
  const [feedTypeId, setFeedTypeId] = useState("");
  const [rowsByBatch, setRowsByBatch] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setSelectedIds(current => current.filter(id => batches.some(batch => batch.id === id)));
    if (!feedBatchId || !batches.some(batch => batch.id === feedBatchId)) setFeedBatchId(initialBatchId || batches[0]?.id || "");
  }, [batches, feedBatchId, initialBatchId]);

  useEffect(() => {
    const requestedIds = [...new Set([...selectedIds, feedBatchId].filter(Boolean))];
    if (!isValidDateRange(dateRange) || requestedIds.length === 0) { setRowsByBatch({}); setLoading(false); setError(""); return undefined; }
    const dayCount = Math.floor((Date.parse(`${dateRange.to}T00:00:00Z`) - Date.parse(`${dateRange.from}T00:00:00Z`)) / 86400000) + 1;
    if (dayCount > 366) { setError("Choose a date range of 366 days or fewer for operations charts."); setRowsByBatch({}); setLoading(false); return undefined; }
    let current = true;
    setLoading(true);
    setError("");
    Promise.all(requestedIds.map(async id => [id, await dailyApi.list(id, dateRange.from, dateRange.to)]))
      .then(entries => { if (current) setRowsByBatch(previous => ({ ...previous, ...Object.fromEntries(entries) })); })
      .catch(err => { if (current) { setRowsByBatch({}); setError(err.message || "Could not load operations charts."); } })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [selectedIds, feedBatchId, dateRange?.from, dateRange?.to]);

  const selectedFeed = rowsByBatch[feedBatchId] || [];
  const feedTypes = [...new Map(selectedFeed.flatMap(row => row.feed || []).map(item => [item.feedTypeId, item])).values()];
  const effectiveFeedType = feedTypes.some(item => item.feedTypeId === feedTypeId) ? feedTypeId : feedTypes[0]?.feedTypeId || "";
  const feedData = useMemo(() => {
    let cumulative = 0;
    const daily = new Map();
    selectedFeed.forEach(row => (row.feed || []).filter(item => item.feedTypeId === effectiveFeedType).forEach(item => {
      daily.set(row.date, (daily.get(row.date) || 0) + Number(item.quantity || 0));
    }));
    return [...daily].sort(([a], [b]) => a.localeCompare(b)).map(([date, quantity]) => {
      cumulative += quantity;
      return { date, quantity, cumulative };
    });
  }, [selectedFeed, effectiveFeedType]);
  const mortalityData = useMemo(() => {
    const grouped = new Map();
    for (const id of selectedIds) {
      for (const row of rowsByBatch[id] || []) {
        if (Number(row.mortality || 0) <= 0) continue;
        const point = grouped.get(row.date) || { date: row.date };
        point[id] = Number(row.mortality || 0);
        grouped.set(row.date, point);
      }
    }
    return [...grouped.values()].sort((a, b) => a.date.localeCompare(b.date));
  }, [rowsByBatch, selectedIds]);

  function toggleBatch(id) {
    setSelectedIds(current => current.includes(id)
      ? current.filter(value => value !== id)
      : current.length < 8 ? [...current, id] : current);
  }

  return <>
    <div className="analytics-range-filter"><DateRangeFilter value={dateRange} timezone={timezone} dateFormat={dateFormat} onChange={setDateRange} /></div>
    <div className="analytics-chart-grid">
    <ChartPanel title="Feed consumption" description={`Cumulative recorded feed for ${batches.find(batch => batch.id === feedBatchId)?.code || "the selected batch"} in the selected period. Units are not mixed.`} error={error} empty={!loading && !feedData.length && "No feed usage records are available for this batch and period."} controls={<div className="analytics-chart-filter-row"><label className="analytics-chart-filter">Batch<select aria-label="Feed chart batch" value={feedBatchId} onChange={event => setFeedBatchId(event.target.value)}>{batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}</select></label><label className="analytics-chart-filter">Feed type<select aria-label="Feed chart type" value={effectiveFeedType} onChange={event => setFeedTypeId(event.target.value)}>{feedTypes.map(item => <option key={item.feedTypeId} value={item.feedTypeId}>{item.feedTypeName}{item.brand ? ` · ${item.brand}` : ""} ({item.unit})</option>)}</select></label></div>}>
      {loading ? <p className="muted">Loading chart data…</p> : error ? <p className="error" role="alert">{error}</p> : feedData.length > 0 && <div className="analytics-chart"><ResponsiveContainer width="100%" height={250}><AreaChart data={feedData} margin={{ top: 8, right: 12, bottom: 8, left: 8 }}><CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="date" minTickGap={25} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip />} /><Area type="monotone" dataKey="cumulative" name={`Cumulative (${feedTypes.find(item => item.feedTypeId === effectiveFeedType)?.unit || "unit"})`} stroke={CHART_COLORS[0]} fill={CHART_COLORS[0]} fillOpacity={0.22} isAnimationActive={false} /></AreaChart></ResponsiveContainer></div>}
    </ChartPanel>
    <ChartPanel title="Mortality trends" description="Daily mortality by selected batch. Choose up to eight batches; no-event dates are left as gaps." error={error} empty={!loading && !mortalityData.length && "No mortality events are recorded for the selected batches in this period."} controls={<div className="analytics-batch-filters" aria-label="Mortality chart batches">{batches.map(batch => <label key={batch.id}><input type="checkbox" checked={selectedIds.includes(batch.id)} onChange={() => toggleBatch(batch.id)} />{batch.code}</label>)}</div>}>
      {loading ? <p className="muted">Loading chart data…</p> : error ? <p className="error" role="alert">{error}</p> : mortalityData.length > 0 && <div className="analytics-chart"><ResponsiveContainer width="100%" height={250}><LineChart data={mortalityData} margin={{ top: 8, right: 12, bottom: 8, left: 8 }}><CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="date" minTickGap={25} tick={{ fontSize: 10, fill: "var(--muted)" }} /><YAxis allowDecimals={false} tick={{ fontSize: 10, fill: "var(--muted)" }} /><Tooltip content={<ChartTooltip />} /><Legend />{selectedIds.map((id, index) => <Line key={id} type="monotone" dataKey={id} name={batches.find(batch => batch.id === id)?.code || id} stroke={CHART_COLORS[index % CHART_COLORS.length]} connectNulls={false} dot={false} isAnimationActive={false} />)}</LineChart></ResponsiveContainer></div>}
    </ChartPanel>
    </div>
  </>;
}
