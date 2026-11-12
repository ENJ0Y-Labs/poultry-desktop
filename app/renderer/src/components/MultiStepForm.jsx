import { useRef, useState } from "react";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ValidatedForm from "./ValidatedForm.jsx";
import { getFormControlKey, validateForm } from "../utils/formValidation.js";

export default function MultiStepForm({
  steps,
  onSubmit,
  loading = false,
  submitLabel = "Save",
  className = "form-grid",
}) {
  const [activeStep, setActiveStep] = useState(0);
  const [stepErrors, setStepErrors] = useState({});
  const stepPanelRef = useRef(null);
  const lastStep = steps.length - 1;
  const step = steps[activeStep];

  function nextStep() {
    const controls = [...(stepPanelRef.current?.querySelectorAll("input, select, textarea") || [])]
      .filter(control => !control.disabled && control.type !== "hidden");
    const errors = validateForm({ elements: controls });
    if (Object.keys(errors).length) {
      setStepErrors(errors);
      const firstInvalid = controls.find(control => errors[getFormControlKey(control)]);
      firstInvalid?.focus();
      return;
    }
    const invalid = controls.find(control => !control.checkValidity());
    if (invalid) {
      invalid.reportValidity();
      invalid.focus();
      return;
    }
    setStepErrors({});
    setActiveStep(current => Math.min(current + 1, lastStep));
  }

  function previousStep() {
    setStepErrors({});
    setActiveStep(current => Math.max(current - 1, 0));
  }

  return (
    <ValidatedForm className={className} onSubmit={onSubmit}>
      <nav className="workflow-progress" aria-label="Form progress">
        <div className="workflow-progress-copy">
          <span>STEP {activeStep + 1} OF {steps.length}</span>
          <strong>{step.title}</strong>
        </div>
        <ol>
          {steps.map((item, index) => (
            <li key={item.title} className={index < activeStep ? "is-complete" : index === activeStep ? "is-current" : ""}>
              <span aria-hidden="true">{index < activeStep ? "✓" : index + 1}</span>
              <span className="workflow-step-label sr-only">{item.title}</span>
            </li>
          ))}
        </ol>
        <div className="workflow-progress-track" aria-hidden="true">
          <span style={{ width: `${((activeStep + 1) / steps.length) * 100}%` }} />
        </div>
      </nav>

      {steps.map((item, index) => (
        <section
          key={item.title}
          ref={index === activeStep ? stepPanelRef : undefined}
          className="workflow-step-panel"
          hidden={index !== activeStep}
          aria-hidden={index !== activeStep}
          aria-label={item.title}
          onChangeCapture={event => {
            const key = getFormControlKey(event.target);
            setStepErrors(current => {
              if (!current[key]) return current;
              const next = { ...current };
              delete next[key];
              return next;
            });
          }}
        >
          {item.description && <p className="muted workflow-step-description">{item.description}</p>}
          {index === activeStep && Object.keys(stepErrors).length > 0 && (
            <div className="validation-summary" role="alert" aria-live="polite">
              <strong>Please correct the following fields before continuing:</strong>
              <ul>{Object.values(stepErrors).map((message, errorIndex) => <li key={errorIndex}>{message}</li>)}</ul>
            </div>
          )}
          {item.content}
        </section>
      ))}

      <footer className="workflow-actions">
        <button type="button" className="secondary-button" onClick={previousStep} disabled={activeStep === 0 || loading}>
          Back
        </button>
        {activeStep < lastStep ? (
          <button type="button" onClick={nextStep} disabled={loading}>
            Continue
          </button>
        ) : (
          <button type="submit" disabled={loading}>
            {loading ? "Saving..." : submitLabel}
          </button>
        )}
      </footer>
    </ValidatedForm>
  );
}
