import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DateRangeFilter from "./DateRangeFilter.jsx";

describe("DateRangeFilter", () => {
  it("offers all reporting presets and updates the range in the farm timezone", () => {
    const onChange = vi.fn();
    render(<DateRangeFilter value={{ preset: "LAST_30_DAYS", from: "2024-02-01", to: "2024-03-01" }} timezone="UTC" onChange={onChange} />);
    fireEvent.change(screen.getByRole("combobox", { name: "Reporting period" }), { target: { value: "LAST_MONTH" } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ preset: "LAST_MONTH", from: expect.any(String), to: expect.any(String) }));
    expect(screen.getByRole("option", { name: "Today" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Yesterday" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Last 7 days" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Last 30 days" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Last 90 days" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "This month" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Last month" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "This year" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Custom range" })).toBeInTheDocument();
  });

  it("switches to custom range when a date is edited and reports invalid boundaries", () => {
    const onChange = vi.fn();
    const value = { preset: "LAST_7_DAYS", from: "2024-02-24", to: "2024-03-01" };
    const { rerender } = render(<DateRangeFilter value={value} timezone="UTC" onChange={onChange} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Range start date" }), { target: { value: "2024-03-02" } });
    expect(onChange).toHaveBeenCalledWith({ preset: "CUSTOM", from: "2024-03-02", to: "2024-03-01" });
    rerender(<DateRangeFilter value={{ preset: "CUSTOM", from: "2024-03-02", to: "2024-03-01" }} timezone="UTC" onChange={onChange} />);
    expect(screen.getByRole("alert")).toHaveTextContent("start date is on or before the end date");
  });

  it("shows the selected inclusive range", () => {
    render(<DateRangeFilter value={{ preset: "CUSTOM", from: "2024-02-29", to: "2024-03-01" }} timezone="UTC" onChange={() => {}} />);
    expect(screen.getByText("Showing 29 Feb 2024 – 01 Mar 2024 (inclusive)")).toBeInTheDocument();
  });
});
