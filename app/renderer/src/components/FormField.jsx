/**
 * Accessible field wrapper. Validation rules remain in the owning form and API.
 */
export default function FormField({
  id,
  label,
  children,
  hint,
  error,
  required = false,
  className = "field",
}) {
  return (
    <div className={`form-field ${className}`.trim()}>
      <label htmlFor={id}>
        <span>{label}{required && <span aria-hidden="true"> *</span>}</span>
      </label>
      {children}
      {hint && <p className="form-field-hint" id={`${id}-hint`}>{hint}</p>}
      {error && <p className="form-field-error" id={`${id}-error`} role="alert">{error}</p>}
    </div>
  );
}
