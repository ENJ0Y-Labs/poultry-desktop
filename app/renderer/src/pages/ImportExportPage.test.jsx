import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
// eslint-disable-next-line no-unused-vars
import ImportExportPage from "./ImportExportPage.jsx";

describe("ImportExportPage", () => {
  it("marks historical batch import as unavailable and provides a manual batch workflow", () => {
    render(<ImportExportPage />);
    expect(screen.getByRole("heading", { name: "Existing batch opening records" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Batch import unavailable" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Open batch management" }));
    expect(window.location.hash).toBe("#flocks/flocks");
  });
  it("exposes house/pen, customer, and supplier CSV import modules", () => {
    render(<ImportExportPage />);
    const selector = screen.getByRole("combobox", { name: "Import module" });
    expect(selector).toHaveValue("customers");
    expect(Array.from(selector.options).map(option => option.value)).toEqual(["houses", "customers", "suppliers"]);
  });
  it("links to the existing supported report export workflow", () => {
    render(<ImportExportPage />);
    fireEvent.click(screen.getByRole("button", { name: "Open report exports" }));
    expect(window.location.hash).toBe("#reports/farm-report");
  });
});
