import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import MetricHelp from "./MetricHelp.jsx";

describe("MetricHelp", () => {
  it("reveals the formula help from its keyboard-focusable summary control", () => {
    render(
      <MetricHelp id="fcr-help" label="FCR formula">
        Feed consumed in kilograms divided by live-weight gain in kilograms.
      </MetricHelp>
    );

    const trigger = screen.getByRole("group", { hidden: true });
    expect(trigger).toBeInTheDocument();
    const summary = screen.getByRole("button", { name: "Help: FCR formula" });
    expect(summary.tagName).toBe("SUMMARY");
    expect(screen.queryByRole("tooltip")).not.toBeVisible();

    fireEvent.click(summary);

    expect(screen.getByRole("tooltip")).toHaveTextContent(
      "Feed consumed in kilograms divided by live-weight gain in kilograms."
    );
  });
});
