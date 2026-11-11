import { normalizeFieldKey } from "../utils/formValidation.js";

/** Accessible field wrapper; validation remains in the owning form and API. */
export default function FormField({ id, label, children, hint, error, required = false, className = "field" }) {
  const hintId = id && hint ? `${id}-hint` : undefined;
  const errorId = id && error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  const controls = Children.map(children, child => {
    if (!isValidElement(child) || !["input", "select", "textarea"].includes(child.type)) return child;
    return cloneElement(child, {
      className: ["ui-control", child.props.className].filter(Boolean).join(" "),
      "aria-describedby": [child.props["aria-describedby"], describedBy].filter(Boolean).join(" ") || undefined,
      "aria-invalid": error ? "true" : child.props["aria-invalid"],
      required: required || child.props.required,
    });
  });
  const labelContent = <span>{label}{required && <span aria-hidden="true"> *</span>}</span>;
  return (
    <div className={`form-field ${className}`.trim()}>
      {id ? <><label htmlFor={id}>{labelContent}</label>{children}</> : <label className="form-field-label">{labelContent}{children}</label>}
      {hint && <p className="form-field-hint" id={id ? `${id}-hint` : undefined}>{hint}</p>}
      {error && <p className="form-field-error" id={id ? `${id}-error` : `${normalizeFieldKey(label)}-error`} aria-live="polite">{error}</p>}
    </div>
  );
}
