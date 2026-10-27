/**
 * Shared page-level layout boundary. Domain content and route visibility stay
 * with the owning page and PageSection.
 */
export default function PageTemplate({ title, description, actions, children, className = "" }) {
  return (
    <main className={`page-template ${className}`.trim()} aria-labelledby={title ? "page-template-title" : undefined}>
      {(title || description || actions) && (
        <header className="page-template-header">
          <div className="page-template-heading">
            {title && <h1 id="page-template-title">{title}</h1>}
            {description && <p className="muted">{description}</p>}
          </div>
          {actions && <div className="page-template-actions">{actions}</div>}
        </header>
      )}
      <div className="page-template-content">{children}</div>
    </main>
  );
}
