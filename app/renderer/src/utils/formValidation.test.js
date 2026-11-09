import { describe, expect, it } from "vitest";
import { mapBackendFieldErrors, normalizeFieldKey, validateForm } from "./formValidation.js";

function formWith(markup) {
  const form = document.createElement("form");
  form.innerHTML = markup;
  return form;
}

describe("form validation", () => {
  it("normalizes field keys for backend field matching", () => {
    expect(normalizeFieldKey("Price per crate (₦)")).toBe("price_per_crate");
  });

  it("reports required fields without discarding entered values", () => {
    const form = formWith('<label>Batch<select required><option value="">Choose</option><option value="b1">B1</option></select></label><label>Notes<input value="keep this"></label>');
    const errors = validateForm(form);
    expect(Object.values(errors)).toContain("Batch is required.");
    expect(form.querySelector("input").value).toBe("keep this");
  });

  it("rejects negative numeric values and values outside explicit bounds", () => {
    const form = formWith('<label>Quantity<input type="number" value="-2"></label><label>Bird count<input type="number" min="1" value="0"></label>');
    expect(Object.values(validateForm(form))).toEqual([
      "Quantity cannot be negative.",
      "Bird count must be at least 1."
    ]);
  });

  it("rejects malformed email and missing dates", () => {
    const form = formWith('<label>Email<input type="email" value="not-an-email"></label><label>Date<input type="date" required></label>');
    expect(Object.values(validateForm(form))).toHaveLength(2);
  });

  it("maps backend validation details only to matching controls", () => {
    const form = formWith('<label>Phone<input name="phone"></label><label>Quantity<input name="quantity"></label>');
    expect(mapBackendFieldErrors({ phone: "Invalid phone", missing: "Not found" }, form)).toEqual({ phone: "Invalid phone" });
  });
});
