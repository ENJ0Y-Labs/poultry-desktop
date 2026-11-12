import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import MultiStepForm from "./MultiStepForm.jsx";

describe("MultiStepForm", () => {
  const steps = [
    { title: "Details", content: <label>Batch code<input required name="batchCode" /></label> },
    { title: "Quantity", content: <label>Bird count<input required name="birdCount" type="number" min="1" /></label> },
    { title: "Review", content: <p>Review the complete record.</p> },
  ];

  it("validates the current step before continuing", () => {
    render(<MultiStepForm steps={steps} onSubmit={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByLabelText("Batch code")).toBeInvalid();
    expect(screen.getByText("STEP 1 OF 3")).toBeInTheDocument();
  });

  it("preserves values when moving forward and backward", () => {
    render(<MultiStepForm steps={steps} onSubmit={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Batch code"), { target: { value: "B-2027-001" } });
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    fireEvent.change(screen.getByLabelText("Bird count"), { target: { value: "250" } });
    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByLabelText("Batch code")).toHaveValue("B-2027-001");
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByLabelText("Bird count")).toHaveValue(250);
  });

  it("submits once from the review step after validating all fields", async () => {
    const submit = vi.fn();
    render(<MultiStepForm steps={steps} onSubmit={submit} />);
    fireEvent.change(screen.getByLabelText("Batch code"), { target: { value: "B-2027-001" } });
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    fireEvent.change(screen.getByLabelText("Bird count"), { target: { value: "250" } });
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(submit).toHaveBeenCalledTimes(1);
  });
});
