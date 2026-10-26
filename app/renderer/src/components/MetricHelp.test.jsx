import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
// eslint-disable-next-line no-unused-vars
import MetricHelp from "./MetricHelp.jsx";

describe("MetricHelp", () => {
  it("reveals the formula help from its keyboard-focusable summary control", () => {
    const { container } = render(
      <MetricHelp id="fcr-help" label="FCR formula">
        Feed consumed in kilograms divided by live-weight gain in kilograms.
      </MetricHelp>
    );

    const summary = container.querySelector("summary");
    expect(summary.tagName).toBe("SUMMARY");
    expect(screen.queryByText(/Feed consumed in kilograms/)).not.toBeInTheDocument();

    fireEvent.click(summary);

    expect(screen.getByRole("tooltip")).toHaveTextContent(
      "Feed consumed in kilograms divided by live-weight gain in kilograms."
    );
  });
});
