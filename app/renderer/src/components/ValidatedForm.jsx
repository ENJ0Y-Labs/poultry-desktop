import React, { useCallback, useEffect, useRef, useState } from "react";
import { getFormControlKey, mapBackendFieldErrors, normalizeFieldKey, setActiveValidationForm, validateForm } from "../utils/formValidation.js";

function childText(children) {
  return React.Children.toArray(children).map(child => {
    if (typeof child === "string" || typeof child === "number") return String(child);
    if (!React.isValidElement(child) || child.type === "small" || ["input", "select", "textarea", "button", "option"].includes(child.type)) return "";
    return childText(child.props.children);
  }).join(" ").replace(/\s+/g, " ").trim();
}

function fieldKey(control, inheritedLabel = "") {
  return normalizeFieldKey(control.props.name || inheritedLabel || control.props.id || "field");
}

function enhanceChildren(children, errors, clearError, pending, inheritedLabel = "") {
  return React.Children.map(children, child => {
    if (!React.isValidElement(child)) return child;

    if (child.type === "label") {
      const labelText = childText(child.props.children);
      let controlName = "";
      const inspect = nodes => React.Children.forEach(nodes, node => {
        if (!React.isValidElement(node)) return;
        if (["input", "select", "textarea"].includes(typeof node.type === "string" ? node.type : "")) {
          controlName = fieldKey(node, labelText);
        } else if (node.props.children) inspect(node.props.children);
      });
      inspect(child.props.children);
      const error = errors[controlName];
      const wrapperClass = String(child.props.className || "").split(" ").includes("full") ? "validated-field full" : "validated-field";
      return <div className={wrapperClass}>
        {React.cloneElement(child, {}, enhanceChildren(child.props.children, errors, clearError, pending, labelText))}
        {error && <small id={`${controlName}-error`} className="field-error" role="alert">{error}</small>}
      </div>;
    }

    if (typeof child.type === "string" && ["input", "select", "textarea"].includes(child.type)) {
      const key = fieldKey(child, inheritedLabel);
      const error = errors[key];
      return React.cloneElement(child, {
        "aria-invalid": error ? "true" : undefined,
        "aria-describedby": error ? [child.props["aria-describedby"], `${key}-error`].filter(Boolean).join(" ") : child.props["aria-describedby"],
        onChange: event => {
          child.props.onChange?.(event);
          clearError(key);
        },
      });
    }

    if (typeof child.type === "string" && child.type === "button" &&
      (child.props.type === "submit" || !child.props.type)) {
      return React.cloneElement(child, { disabled: pending || child.props.disabled });
    }

    if (child.props?.children) {
      return React.cloneElement(child, {}, enhanceChildren(child.props.children, errors, clearError, pending, inheritedLabel));
    }
    return child;
  });
}

export default function ValidatedForm({ onSubmit, validate, children, ...props }) {
  const formRef = useRef(null);
  const pendingRef = useRef(false);
  const [errors, setErrors] = useState({});
  const [pending, setPending] = useState(false);

  useEffect(() => {
    function handleBackendValidation(event) {
      if (event.detail?.form && event.detail.form !== formRef.current) return;
      const mapped = mapBackendFieldErrors(event.detail?.details || event.detail, formRef.current);
      if (Object.keys(mapped).length) setErrors(mapped);
    }
    window.addEventListener("pomas:form-validation-error", handleBackendValidation);
    return () => window.removeEventListener("pomas:form-validation-error", handleBackendValidation);
  }, []);

  const clearError = useCallback(key => {
    setErrors(current => {
      if (!current[key]) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });
  }, []);

  async function handleSubmit(event) {
    event.preventDefault();
    if (pendingRef.current) return;

    const nextErrors = { ...validateForm(formRef.current), ...(validate?.(formRef.current) || {}) };
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      const firstInvalid = Array.from(formRef.current?.elements || []).find(control => nextErrors[getFormControlKey(control)]);
      firstInvalid?.focus();
      return;
    }

    pendingRef.current = true;
    setPending(true);
    setActiveValidationForm(formRef.current);
    try {
      await onSubmit?.(event);
    } finally {
      setActiveValidationForm(null);
      pendingRef.current = false;
      setPending(false);
    }
  }

  return <>
    {Object.keys(errors).length > 0 && <div className="validation-summary" role="alert" aria-live="polite">
      <strong>Please correct the following fields:</strong>
      <ul>{Object.values(errors).map((message, index) => <li key={index}>{message}</li>)}</ul>
    </div>}
    <form {...props} ref={formRef} noValidate onSubmit={handleSubmit} aria-busy={pending || undefined}>
      {enhanceChildren(children, errors, clearError, pending)}
    </form>
  </>;
}
