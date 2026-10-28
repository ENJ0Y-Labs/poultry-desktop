/** Shared page layout boundary; domain content stays in its owning page. */
export default function PageTemplate({ title, description, actions, children, className = "" }) {
  return (
    <div className={`page-template ${className}`.trim()} aria-labelledby={title ? "page-template-title" : undefined}>
      {(title || description || actions) && (
        <header className="page-template-header">
          <div className="page-template-heading">
            {title && <h2 id="page-template-title">{title}</h2>}
            {description && <p className="muted">{description}</p>}
          </div>
          {actions && <div className="page-template-actions">{actions}</div>}
        </header>
      )}
      {children}
    </div>
  );
}
