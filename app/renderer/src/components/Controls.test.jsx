import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Button from "./Button.jsx";
import * as FormControls from "./FormControls.jsx";

const h = React.createElement;

describe("shared Button", () => {
  it.each(["primary", "secondary", "outline", "ghost", "destructive"])("supports the %s variant", variant => {
    render(h(Button, { variant, children: "Save" }));
    expect(screen.getByRole("button", { name: "Save" })).toHaveClass(`ui-button--${variant}`);
  });
  it("exposes loading and disabled state and prevents duplicate submission", () => {
    render(h(Button, { loading: true, children: "Saving" }));
    expect(screen.getByRole("button", { name: "Saving" })).toBeDisabled();
    expect(screen.getByRole("button")).toHaveAttribute("aria-busy", "true");
  });
  it("requires an accessible name for icon-only actions", () => {
    render(h(Button, { iconOnly: true, ariaLabel: "Remove item", children: h("span", { "aria-hidden": "true" }, "×") }));
    expect(screen.getByRole("button", { name: "Remove item" })).toHaveAttribute("title", "Remove item");
  });
  it("keeps submit semantics opt-in", () => {
    render(h(Button, { type: "submit", children: "Save" }));
    expect(screen.getByRole("button")).toHaveAttribute("type", "submit");
  });
});

describe("shared form controls", () => {
  it.each([
    ["Text", h(FormControls.TextInput, { label: "Farm name" }), "Farm name"],
    ["Number", h(FormControls.NumberInput, { label: "Bird count" }), "Bird count"],
    ["Currency", h(FormControls.CurrencyInput, { label: "Unit price" }), "Unit price"],
    ["Date", h(FormControls.DateInput, { label: "Record date" }), "Record date"],
    ["Search", h(FormControls.SearchInput, { label: "Search records" }), "Search records"],
    ["Select", h(FormControls.SelectInput, { label: "House", children: h("option", null, "House A") }), "House"],
    ["Textarea", h(FormControls.TextareaInput, { label: "Notes" }), "Notes"],
    ["Checkbox", h(FormControls.CheckboxInput, { label: "Confirm" }), "Confirm"],
    ["Radio", h(FormControls.RadioInput, { label: "Layer", name: "batch-type" }), "Layer"],
    ["Switch", h(FormControls.SwitchInput, { label: "Enable alerts" }), "Enable alerts"],
  ])("renders an accessible %s control", (_kind, control, accessibleName) => {
    render(control);
    expect(screen.getByLabelText(accessibleName)).toBeInTheDocument();
  });
  it("connects helper and validation text to the control", () => {
    render(h(FormControls.TextInput, { label: "Farm name", hint: "Use the registered name", error: "Name is required" }));
    const field = screen.getByLabelText("Farm name");
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(field).toHaveAccessibleDescription("Use the registered name Name is required");
    expect(screen.getByRole("alert")).toHaveTextContent("Name is required");
  });
  it("preserves controlled values and callbacks", () => {
    const onChange = vi.fn();
    render(h(FormControls.TextInput, { label: "Farm name", value: "Grantino", onChange }));
    fireEvent.change(screen.getByLabelText("Farm name"), { target: { value: "Pomas" } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});
