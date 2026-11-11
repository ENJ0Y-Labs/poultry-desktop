import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
// eslint-disable-next-line no-unused-vars
import ImportExportPage from "./ImportExportPage.jsx";

describe("ImportExportPage", () => {
  it("offers a working batch opening import path", () => {
    render(<ImportExportPage />);
    expect(screen.getByRole("heading", { name: "Batch / flock opening records" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Prepare batch import" }));
    expect(screen.getByRole("combobox", { name: "Import module" })).toHaveValue("batches");
  });
  it("exposes house/pen, feed, production, health, drug, vaccination, customer, and supplier CSV import modules", () => {
    render(<ImportExportPage />);
    const selector = screen.getByRole("combobox", { name: "Import module" });
    expect(selector).toHaveValue("customers");
    expect(Array.from(selector.options).map(option => option.value)).toEqual(["houses", "batches", "feed-purchases", "feed-usage", "egg-collections", "broiler-weights", "health", "drugs", "vaccinations", "customers", "suppliers"]);
  });
  it("links to the existing supported report export workflow", () => {
    render(<ImportExportPage />);
    fireEvent.click(screen.getByRole("button", { name: "Open report exports" }));
    expect(window.location.hash).toBe("#reports/farm-report");
  });
});
