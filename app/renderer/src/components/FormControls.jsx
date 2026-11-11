import React, { useId } from "react";

const h = React.createElement;

function Field({ label, hint, error, required, id, children, className = "" }) {
  const generatedId = useId();
  const controlId = id || generatedId;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  return h("div", { className: ["ui-field", error && "has-error", className].filter(Boolean).join(" ") },
    label ? h("label", { className: "ui-field__label", htmlFor: controlId },
      label, required && h("span", { "aria-hidden": "true" }, " *")) : null,
    children({ id: controlId, "aria-describedby": describedBy, "aria-invalid": error ? "true" : undefined, required }),
    hint ? h("p", { className: "ui-field__hint", id: hintId }, hint) : null,
    error ? h("p", { className: "ui-field__error", id: errorId, role: "alert" }, error) : null
  );
}

function input(type) {
  return function InputControl({ label, hint, error, required, className = "", ...props }) {
    return h(Field, { label, hint, error, required, className },
      attrs => h("input", { ...props, ...attrs, type, className: "ui-control" }));
  };
}

export const TextInput = input("text");
export const NumberInput = input("number");
export const CurrencyInput = input("text");
export const DateInput = input("date");
export const SearchInput = input("search");

export function SelectInput({ label, hint, error, required, children, className = "", ...props }) {
  return h(Field, { label, hint, error, required, className },
    attrs => h("select", { ...props, ...attrs, className: "ui-control" }, children));
}

export function TextareaInput({ label, hint, error, required, className = "", ...props }) {
  return h(Field, { label, hint, error, required, className },
    attrs => h("textarea", { ...props, ...attrs, className: "ui-control" }));
}

export function CheckboxInput({ label, hint, error, required, className = "", ...props }) {
  return h(Field, { hint, error, className: `ui-field--choice ${className}` },
    attrs => h("label", { className: "ui-choice" },
      h("input", { ...props, ...attrs, type: "checkbox" }),
      h("span", null, label),
      required && h("span", { "aria-hidden": "true" }, " *")));
}

export function RadioInput({ label, hint, error, required, className = "", ...props }) {
  return h(Field, { hint, error, className: `ui-field--choice ${className}` },
    attrs => h("label", { className: "ui-choice" },
      h("input", { ...props, ...attrs, type: "radio" }),
      h("span", null, label),
      required && h("span", { "aria-hidden": "true" }, " *")));
}

export function SwitchInput({ label, hint, error, required, className = "", ...props }) {
  return h(Field, { hint, error, className: `ui-field--choice ${className}` },
    attrs => h("label", { className: "ui-choice ui-switch" },
      h("input", { ...props, ...attrs, type: "checkbox", role: "switch" }),
      h("span", null, label),
      required && h("span", { "aria-hidden": "true" }, " *")));
}
