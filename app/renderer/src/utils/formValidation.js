import { parseNairaToKobo } from "./moneyInput.js";

const FIELD_LABEL_IGNORE = /\s+/g;

export function normalizeFieldKey(value) {
  return String(value ?? "")
    .trim()
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

export function formatFieldLabel(value) {
  const label = String(value ?? "").replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ").replace(FIELD_LABEL_IGNORE, " ").trim();
  const cleaned = label.replace(/\s*\*$/, "");
  return cleaned ? cleaned[0].toUpperCase() + cleaned.slice(1) : "This field";
}

let activeValidationForm = null;

export function setActiveValidationForm(form) {
  activeValidationForm = form || null;
}

export function getActiveValidationForm() {
  return activeValidationForm;
}

function labelFor(control) {
  const label = control.closest("label");
  if (!label) return formatFieldLabel(control.name || control.getAttribute("aria-label") || control.id);
  const clone = label.cloneNode(true);
  clone.querySelectorAll("small, input, select, textarea, button, .required-indicator, .field-error").forEach(node => node.remove());
  return formatFieldLabel(clone.textContent);
}

export function getFormControlKey(control) {
  return normalizeFieldKey(control.name || labelFor(control));
}

function validCalendarDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function validateForm(form) {
  const errors = {};
  const controls = Array.from(form.elements || []).filter(control =>
    control && !control.disabled && control.type !== "submit" &&
    control.type !== "button" && control.type !== "reset" && control.type !== "hidden"
  );

  for (const control of controls) {
    const key = getFormControlKey(control);
    const label = labelFor(control);
    const value = String(control.value ?? "").trim();
    let message = "";

    if (control.required && (control.type === "checkbox" || control.type === "radio")
      ? !control.checked
      : control.required && !value) {
      message = `${label} is required.`;
    } else if (value && control.type === "number") {
      const number = Number(value);
      if (!Number.isFinite(number)) message = `Enter a valid number for ${label.toLowerCase()}.`;
      else if (number < 0 && !control.hasAttribute("min")) message = `${label} cannot be negative.`;
      else if (control.min !== "" && number < Number(control.min)) message = `${label} must be at least ${control.min}.`;
      else if (control.max !== "" && number > Number(control.max)) message = `${label} must be no more than ${control.max}.`;
      else if (control.validity?.stepMismatch) message = `Enter a valid value for ${label.toLowerCase()}.`;
    } else if (value && /₦|\b(amount|price|cost|revenue)\b/i.test(label) && control.type !== "number") {
      try { parseNairaToKobo(value, { allowZero: /purchase cost/i.test(label) }); }
      catch (error) { message = error.message; }
    } else if (value && control.type === "email" && control.validity?.typeMismatch) {
      message = `Enter a valid email address for ${label.toLowerCase()}.`;
    } else if (value && control.type === "date") {
      if (!validCalendarDate(value)) message = `Enter a valid date for ${label.toLowerCase()}.`;
      else if (control.min && value < control.min) message = `${label} must be on or after ${control.min}.`;
      else if (control.max && value > control.max) message = `${label} must be on or before ${control.max}.`;
    } else if (value && control.minLength > 0 && value.length < control.minLength) {
      message = `${label} must contain at least ${control.minLength} characters.`;
    } else if (value && control.maxLength > 0 && value.length > control.maxLength) {
      message = `${label} must contain no more than ${control.maxLength} characters.`;
    } else if (value && control.pattern && !new RegExp(`^(?:${control.pattern})$`).test(value)) {
      message = `Enter a valid ${label.toLowerCase()}.`;
    }

    if (message) errors[key] = message;
  }

  return errors;
}

export function mapBackendFieldErrors(details, form) {
  if (!details || typeof details !== "object" || Array.isArray(details)) return {};
  const fieldDetails = details.fields && typeof details.fields === "object" ? details.fields : details;
  const controls = Array.from(form?.elements || []);
  const errors = {};
  for (const [field, rawMessage] of Object.entries(fieldDetails)) {
    const message = Array.isArray(rawMessage) ? rawMessage.join(", ") : String(rawMessage ?? "");
    if (!message) continue;
    const normalizedField = normalizeFieldKey(field);
    const aliases = {
      initial_bird_count: "initial_birds", sample_quantity: "sampled_birds",
      total_weight_kg: "total_sample_weight_kg", purchase_cost_recorded: "purchase_information",
      new_count: "physical_count", movement_type: "movement", target_margin_percent: "target_margin",
    };
    const key = aliases[normalizedField] || (normalizedField.endsWith("_date") ? "date" : normalizedField.replace(/_(minor|id|percent)$/g, ""));
    const matching = controls.find(control => {
      const normalizedCandidate = normalizeFieldKey(control.name || labelFor(control));
      const candidate = aliases[normalizedCandidate] || (normalizedCandidate.endsWith("_date") ? "date" : normalizedCandidate.replace(/_(minor|id|percent)$/g, ""));
      const label = normalizeFieldKey(labelFor(control)).replace(/_(minor|id|percent)$/g, "");
      return candidate === key || label === key || label.includes(key) || key.includes(label);
    });
    if (matching) errors[getFormControlKey(matching)] = message;
  }
  return errors;
}
