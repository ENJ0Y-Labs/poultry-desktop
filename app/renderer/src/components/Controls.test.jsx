import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Button from "./Button.jsx";
import { CheckboxInput, DateInput, NumberInput, SearchInput, SelectInput, SwitchInput, TextInput, TextareaInput } from "./FormControls.jsx";

describe("shared Button", () => {
  it.each(["primary", "secondary", "outline", "ghost", "destructive"])("supports the %s variant", variant => {
    render(<Button variant={variant}>Save</Button>);
    expect(screen.getByRole("button", { name: "Save" })).toHaveClass(`ui-button--${variant}`);
  });
  it("exposes loading and disabled state and prevents duplicate submission", () => {
    render(<Button loading>Saving</Button>);
    expect(screen.getByRole("button", { name: "Saving" })).toBeDisabled();
    expect(screen.getByRole("button")).toHaveAttribute("aria-busy", "true");
  });
  it("requires an accessible name for icon-only actions", () => {
    render(<Button iconOnly ariaLabel="Remove item"><span aria-hidden="true">×</span></Button>);
    expect(screen.getByRole("button", { name: "Remove item" })).toHaveAttribute("title", "Remove item");
  });
  it("keeps submit semantics opt-in", () => {
    render(<Button type="submit">Save</Button>);
    expect(screen.getByRole("button")).toHaveAttribute("type", "submit");
  });
});

describe("shared form controls", () => {
  it.each([
    ["Text", <TextInput label="Farm name" />],
    ["Number", <NumberInput label="Bird count" />],
    ["Date", <DateInput label="Record date" />],
    ["Search", <SearchInput label="Search records" />],
    ["Select", <SelectInput label="House"><option>House A</option></SelectInput>],
    ["Textarea", <TextareaInput label="Notes" />],
    ["Checkbox", <CheckboxInput label="Confirm" />],
    ["Switch", <SwitchInput label="Enable alerts" />],
  ])("renders an accessible %s control", (_kind, control) => {
    render(control);
    expect(screen.getByLabelText(/Farm name|Bird count|Record date|Search records|House|Notes|Confirm|Enable alerts/)).toBeInTheDocument();
  });
  it("connects helper and validation text to the control", () => {
    render(<TextInput label="Farm name" hint="Use the registered name" error="Name is required" />);
    const field = screen.getByLabelText("Farm name");
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(field).toHaveAccessibleDescription("Use the registered name Name is required");
    expect(screen.getByRole("alert")).toHaveTextContent("Name is required");
  });
  it("preserves controlled values and callbacks", () => {
    const onChange = vi.fn();
    render(<TextInput label="Farm name" value="Grantino" onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("Farm name"), { target: { value: "Pomas" } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});
