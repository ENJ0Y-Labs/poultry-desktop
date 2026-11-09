const FIELD_LABEL_IGNORE = /\\s+/g;

export function normalizeFieldKey(value) {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

export function formatFieldLabel(value) {
  const label = String(value ?? "").replace(FIELD_LABEL_IGNORE, " ").trim();
  return label.replace(/\\s*\\*$/, "") || "This field";
}

function labelFor(control) {
  if (control.name) return formatFieldLabel(control.name.replace(/[_-]+/g, " "));
  const label = control.closest("label");
  if (!label) return formatFieldLabel(control.getAttribute("aria-label") || control.id);
  const clone = label.cloneNode(true);
  clone.querySelectorAll("small, .required-indicator, .field-error").forEach(node => node.remove());
  return formatFieldLabel(clone.textContent);
}

function fieldKey(control) {
  return normalizeFieldKey(control.name || labelFor(control));
}

function validCalendarDate(value) {
  if (!/^\\d{4}-\\d{2}-\\d{2}$/.test(value)) return false;
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
    const key = fieldKey(control);
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
    } else if (value && control.type === "email" && control.validity?.typeMismatch) {
      message = `Enter a valid email address for ${label.toLowerCase()}.`;
    } else if (value && control.type === "date" && !validCalendarDate(value)) {
      message = `Enter a valid date for ${label.toLowerCase()}.`;
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
  const controls = Array.from(form?.elements || []);
  const errors = {};
  for (const [field, rawMessage] of Object.entries(details)) {
    const message = Array.isArray(rawMessage) ? rawMessage.join(", ") : String(rawMessage ?? "");
    if (!message) continue;
    const key = normalizeFieldKey(field);
    const matching = controls.find(control =>
      normalizeFieldKey(control.name || labelFor(control)) === key ||
      normalizeFieldKey(labelFor(control)).includes(key) ||
      key.includes(normalizeFieldKey(labelFor(control)))
    );
    if (matching) errors[fieldKey(matching)] = message;
  }
  return errors;
}
