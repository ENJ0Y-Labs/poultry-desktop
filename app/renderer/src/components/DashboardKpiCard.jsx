export default function DashboardKpiCard({
  label,
  value,
  unit,
  primary = false,
  href,
  description,
  comparison,
  status,
}) {
  const content = (
    <>
      <span className="dashboard-kpi-card-label">{label}</span>
      <strong className="dashboard-kpi-card-value">{value ?? "—"}</strong>
      {unit ? <span className="dashboard-kpi-card-unit">{unit}</span> : null}
      {comparison?.currentPeriod && comparison?.previousPeriod && comparison?.value != null ? (
        <span className="dashboard-kpi-card-hint">
          {comparison.value} · {comparison.currentPeriod} vs {comparison.previousPeriod}
        </span>
      ) : null}
      {status ? <span className="dashboard-kpi-card-hint">{status}</span> : null}
    </>
  );

  const className = "dashboard-kpi-card" + (primary ? " dashboard-kpi-card-primary" : "");
  if (href) {
    return (
      <button
        type="button"
        className={className}
        onClick={() => { window.location.hash = href.replace(/^#/, ""); }}
        aria-label={description ? `${label}: ${value ?? "not available"}. ${description}` : undefined}
      >
        {content}
      </button>
    );
  }
  return <div className={className}>{content}</div>;
}
