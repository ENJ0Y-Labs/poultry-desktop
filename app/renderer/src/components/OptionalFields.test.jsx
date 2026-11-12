import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import OptionalFields from "./OptionalFields.jsx";

describe("OptionalFields", () => {
  it("starts collapsed and preserves child values when collapsed and reopened", () => {
    render(<OptionalFields title="Extra details"><label>Notes<input defaultValue="Keep this note" /></label></OptionalFields>);
    const section = screen.getByText("Extra details").closest("details");
    const input = screen.getByLabelText("Notes");
    expect(section.open).toBe(false);
    expect(input.value).toBe("Keep this note");
    fireEvent.click(screen.getByText("Extra details"));
    expect(section.open).toBe(true);
    fireEvent.change(input, { target: { value: "Updated note" } });
    fireEvent.click(screen.getByText("Extra details"));
    expect(section.open).toBe(false);
    expect(input.value).toBe("Updated note");
    fireEvent.click(screen.getByText("Extra details"));
    expect(input.value).toBe("Updated note");
  });

  it("announces optional fields without changing required controls", () => {
    render(<OptionalFields><label>Optional notes<input /></label></OptionalFields>);
    expect(screen.getByText("Optional")).toBeTruthy();
    expect(screen.getByLabelText("Optional notes").required).toBe(false);
  });
});
