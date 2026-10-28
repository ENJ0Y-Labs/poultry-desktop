/** Accessible field wrapper; validation remains in the owning form and API. */
export default function FormField({ id, label, children, hint, error, required = false, className = "field" }) {
  const labelContent = <span>{label}{required && <span aria-hidden="true"> *</span>}</span>;
  return (
    <div className={`form-field ${className}`.trim()}>
      {id ? <><label htmlFor={id}>{labelContent}</label>{children}</> : <label className="form-field-label">{labelContent}{children}</label>}
      {hint && <p className="form-field-hint" id={id ? `${id}-hint` : undefined}>{hint}</p>}
      {error && <p className="form-field-error" id={id ? `${id}-error` : undefined} role="alert">{error}</p>}
    </div>
  );
}
