import React from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ValidatedForm from "./ValidatedForm.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import FormField from "./FormField.jsx";

describe("ValidatedForm", () => {
  it("shows a summary and inline required-field error while preserving entered values", async () => {
    const submit = vi.fn();
    render(<ValidatedForm onSubmit={submit}>
      <label>Batch<input name="batch" required defaultValue="" /></label>
      <label>Notes<input name="notes" defaultValue="keep these notes" /></label>
      <button type="submit">Save</button>
    </ValidatedForm>);

    fireEvent.submit(screen.getByRole("button", { name: "Save" }).closest("form"));

    expect(await screen.findByText("Please correct the following fields:")).toBeTruthy();
    expect(document.getElementById("batch-error").textContent).toBe("Batch is required.");
    expect(document.activeElement).toBe(screen.getByLabelText("Batch"));
    expect(screen.getByLabelText("Notes").value).toBe("keep these notes");
    expect(submit).not.toHaveBeenCalled();
  });

  it("supports the existing FormField wrapper and links its inline error", async () => {
    render(<ValidatedForm onSubmit={vi.fn()}>
      <FormField label="New category" required><input required defaultValue="" /></FormField>
      <button type="submit">Save</button>
    </ValidatedForm>);

    fireEvent.submit(screen.getByRole("button", { name: "Save" }).closest("form"));
    expect(document.getElementById("new_category-error").textContent).toBe("New category is required.");
    expect(screen.getByRole("textbox").getAttribute("aria-describedby")).toContain("new_category-error");
  });

  it("prevents another submit while the first request is pending", async () => {
    let finish;
    const submit = vi.fn(() => new Promise(resolve => { finish = resolve; }));
    render(<ValidatedForm onSubmit={submit}>
      <label>Farm name<input name="name" required defaultValue="Grantino" /></label>
      <button type="submit">Save</button>
    </ValidatedForm>);

    const form = screen.getByRole("button", { name: "Save" }).closest("form");
    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(submit).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Save" }).disabled).toBe(true);
    finish();
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" }).disabled).toBe(false));
  });

  it("shows backend field errors beside the matching control", async () => {
    render(<ValidatedForm onSubmit={async () => {
      const form = document.querySelector("form");
      window.dispatchEvent(new CustomEvent("pomas:form-validation-error", {
        detail: { form, details: { fields: { quantity: "Must be a positive whole number." } } }
      }));
    }}>
      <label>Quantity<input name="quantity" defaultValue="2" /></label>
      <button type="submit">Save</button>
    </ValidatedForm>);

    fireEvent.submit(screen.getByRole("button", { name: "Save" }).closest("form"));
    expect(document.getElementById("quantity-error").textContent).toBe("Must be a positive whole number.");
    expect(screen.getByLabelText("Quantity").getAttribute("aria-invalid")).toBe("true");
  });
});
