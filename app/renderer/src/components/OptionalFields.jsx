export default function OptionalFields({ title = "Additional details", description, children }) {
  return (
    <details className="optional-fields">
      <summary>
        <span>{title}</span>
        <span className="optional-fields-hint">Optional</span>
      </summary>
      {description && <p className="muted optional-fields-description">{description}</p>}
      <div className="optional-fields-content">{children}</div>
    </details>
  );
}
