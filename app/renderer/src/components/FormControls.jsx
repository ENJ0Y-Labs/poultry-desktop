import { useId } from "react";

function Field({ label, hint, error, required, id, children, className = "" }) {
  const generatedId = useId();
  const controlId = id || generatedId;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  return (
    <div className={[`ui-field`, error && "has-error", className].filter(Boolean).join(" ")}>
      {label && <label className="ui-field__label" htmlFor={controlId}>{label}{required && <span aria-hidden="true"> *</span>}</label>}
      {children({ id: controlId, "aria-describedby": describedBy, "aria-invalid": error ? "true" : undefined, required })}
      {hint && <p className="ui-field__hint" id={hintId}>{hint}</p>}
      {error && <p className="ui-field__error" id={errorId} role="alert">{error}</p>}
    </div>
  );
}

function input(type) {
  return function InputControl({ label, hint, error, required, className = "", ...props }) {
    return <Field label={label} hint={hint} error={error} required={required} className={className}>{attrs => <input {...props} {...attrs} type={type} className="ui-control" />}</Field>;
  };
}

export const TextInput = input("text");
export const NumberInput = input("number");
export const CurrencyInput = input("text");
export const DateInput = input("date");
export const SearchInput = input("search");

export function SelectInput({ label, hint, error, required, children, className = "", ...props }) {
  return <Field label={label} hint={hint} error={error} required={required} className={className}>{attrs => <select {...props} {...attrs} className="ui-control">{children}</select>}</Field>;
}

export function TextareaInput({ label, hint, error, required, className = "", ...props }) {
  return <Field label={label} hint={hint} error={error} required={required} className={className}>{attrs => <textarea {...props} {...attrs} className="ui-control" />}</Field>;
}

export function CheckboxInput({ label, hint, error, required, className = "", ...props }) {
  return <Field hint={hint} error={error} className={`ui-field--choice ${className}`}>{attrs => <label className="ui-choice"><input {...props} {...attrs} type="checkbox" /> <span>{label}</span>{required && <span aria-hidden="true"> *</span>}</label>}</Field>;
}

export function RadioInput({ label, hint, error, required, className = "", ...props }) {
  return <Field hint={hint} error={error} className={`ui-field--choice ${className}`}>{attrs => <label className="ui-choice"><input {...props} {...attrs} type="radio" /> <span>{label}</span>{required && <span aria-hidden="true"> *</span>}</label>}</Field>;
}

export function SwitchInput({ label, hint, error, required, className = "", ...props }) {
  return <Field hint={hint} error={error} className={`ui-field--choice ${className}`}>{attrs => <label className="ui-choice ui-switch"><input {...props} {...attrs} type="checkbox" role="switch" /> <span>{label}</span>{required && <span aria-hidden="true"> *</span>}</label>}</Field>;
}
