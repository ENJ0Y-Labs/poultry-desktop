export default function MetricHelp({ id, label, children }) {
  return (
    <details className="metric-help">
      <summary aria-label={`Help: ${label}`} aria-controls={id}>
        <span aria-hidden="true">?</span>
      </summary>
      <div className="metric-help-popover" id={id} role="tooltip">
        {children}
      </div>
    </details>
  );
}
