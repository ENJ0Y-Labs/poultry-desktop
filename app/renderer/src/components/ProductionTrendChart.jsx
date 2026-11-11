import { useEffect, useState } from "react";
// eslint-disable-next-line no-unused-vars
import EmptyState from "./EmptyState.jsx";
// JSX component usage is not counted by the configured core no-unused-vars rule.
/* eslint-disable no-unused-vars */
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
/* eslint-enable no-unused-vars */

function formatValue(value, unit) {
  if (value == null || !Number.isFinite(Number(value))) return "No record";
  const numeric = Number(value);
  return unit === "percent" ? `${numeric.toFixed(2)}%` : `${numeric.toFixed(3)} kg`;
}

export default function ProductionTrendChart({ batchId, title, titleHelp, description, unit, loadTrend, dateRange }) {
  const from = dateRange?.from || "";
  const to = dateRange?.to || "";
  const rangeDays = from && to
    ? Math.round((Date.parse(to + "T00:00:00Z") - Date.parse(from + "T00:00:00Z")) / 86400000) + 1
    : 0;
  const rangeSupported = rangeDays > 0 && rangeDays <= 366;
  const [trend, setTrend] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    function refreshAfterMutation(event) {
      const path = event.detail?.path || "";
      const method = String(event.detail?.method || "").toUpperCase();
      if (method === "GET") return;
      const relevant = unit === "percent"
        ? new RegExp("^/batches/" + batchId + "/(?:eggs/(?:collections|sales)|mortality|culling|bird-sales|transfers)(?:/|$|\\?)").test(path)
        : new RegExp("^/batches/" + batchId + "/broiler/weights(?:/|$|\\?)").test(path);
      if (relevant) setRefreshKey(key => key + 1);
    }
    window.addEventListener("pomas:data-mutated", refreshAfterMutation);
    return () => window.removeEventListener("pomas:data-mutated", refreshAfterMutation);
  }, [batchId, unit]);

  useEffect(() => {
    if (!batchId) {
      setTrend(null);
      return undefined;
    }
    if (!rangeSupported) {
      setTrend(null);
      setError("Choose a valid date range of 366 days or fewer to view this production chart.");
      setLoading(false);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError("");
    loadTrend(batchId, from, to)
      .then(data => { if (active) setTrend(data); })
      .catch(err => { if (active) { setTrend(null); setError(err.message || "Could not load production trend."); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [batchId, from, to, rangeSupported, loadTrend, refreshKey]);

  const points = trend?.points || [];
  const hasValues = points.some(point => point.value != null);

  return (
    <section className="subsection production-trend" aria-label={title}>
      <div className="section-head">
        <div>
          <h3>{title}{titleHelp ? <> {titleHelp}</> : null}</h3>
          <p className="muted">{description}</p>
        </div>
      </div>
      <div className="inline-form production-trend-filters">
        <label className="field"><span>From</span><input type="date" aria-label={title + " start date"} value={from} max={to || undefined} onChange={event => setFrom(event.target.value)} /></label>
        <label className="field"><span>To</span><input type="date" aria-label={title + " end date"} value={to} min={from || undefined} onChange={event => setTo(event.target.value)} /></label>
      </div>
      {loading ? <EmptyState variant="loading" heading={`Loading ${title.toLowerCase()}`} compact /> :
        error ? <EmptyState variant="error" description={error} onRetry={() => setRefreshKey(key => key + 1)} compact /> :
          !hasValues ? <EmptyState variant="no-records" heading="No production records in this period" description="Days without records are shown as gaps, not zero. Choose another date range or record production for this batch." compact /> :
            <div role="img" aria-label={`${title} line chart from ${trend.from} to ${trend.to}. Values are provided by the backend.`} className="production-trend-chart">
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={points} margin={{ top: 12, right: 20, left: 4, bottom: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" minTickGap={24} tick={{ fontSize: 11 }} />
                  <YAxis width={58} tick={{ fontSize: 11 }} unit={unit === "percent" ? "%" : " kg"} />
                  <Tooltip labelFormatter={label => `Date: ${label}`} formatter={value => [formatValue(value, unit), title]} />
                  <Line type="monotone" dataKey="value" name={title} stroke="var(--accent)" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }} connectNulls={false} isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>}
    </section>
  );
}
