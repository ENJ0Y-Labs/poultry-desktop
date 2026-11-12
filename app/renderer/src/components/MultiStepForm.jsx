import { useRef, useState } from "react";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ValidatedForm from "./ValidatedForm.jsx";

export default function MultiStepForm({
  steps,
  onSubmit,
  loading = false,
  submitLabel = "Save",
  className = "form-grid",
}) {
  const [activeStep, setActiveStep] = useState(0);
  const stepPanelRef = useRef(null);
  const lastStep = steps.length - 1;
  const step = steps[activeStep];

  function nextStep() {
    const controls = [...(stepPanelRef.current?.querySelectorAll("input, select, textarea") || [])];
    const invalid = controls.find(control => !control.disabled && !control.checkValidity());
    if (invalid) {
      invalid.reportValidity();
      invalid.focus();
      return;
    }
    setActiveStep(current => Math.min(current + 1, lastStep));
  }

  function previousStep() {
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
              <span className="workflow-step-label">{item.title}</span>
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
        >
          {item.description && <p className="muted workflow-step-description">{item.description}</p>}
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
