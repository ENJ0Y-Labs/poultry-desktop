import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
// eslint-disable-next-line no-unused-vars
import EmptyState from "./EmptyState.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "./DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import { FarmManagerContext } from "./FarmManagerContext.jsx";

afterEach(() => cleanup());

describe("EmptyState", () => {
  it.each([
    ["no-records", "Nothing here yet", "status"],
    ["no-results", "No matching records", "status"],
    ["no-search-results", "No search results", "status"],
    ["no-filter-results", "No matching records", "status"],
    ["not-applicable", "Not applicable", "status"],
    ["loading", "Loading records", "status"],
    ["error", "Could not load records", "alert"],
  ])("renders the %s state with the correct accessible role", (variant, heading, role) => {
    render(<EmptyState variant={variant} />);
    expect(screen.getByRole(role)).toHaveTextContent(heading);
  });

  it("preserves legacy child content and renders available actions", () => {
    const onRetry = vi.fn();
    const onClearFilters = vi.fn();
    render(
      <EmptyState variant="no-results" onRetry={onRetry} onClearFilters={onClearFilters}>
        Nothing matched your selected criteria.
      </EmptyState>
    );

    expect(screen.getByText("Nothing matched your selected criteria.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    fireEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(onRetry).toHaveBeenCalledOnce();
    expect(onClearFilters).toHaveBeenCalledOnce();
  });
});

describe("DataTable", () => {
  const columns = [{ key: "name", label: "Name" }];

  it("distinguishes first-use empty data from an empty filtered result", () => {
    const { rerender } = render(<DataTable caption="Houses" columns={columns} rows={[]} />);
    expect(screen.getByRole("status")).toHaveTextContent("Nothing here yet");

    rerender(<DataTable caption="Houses" columns={columns} rows={[]} filtered onClearFilters={() => {}} />);
    expect(screen.getByRole("status")).toHaveTextContent("No matching records");
    expect(screen.getByRole("button", { name: "Clear filters" })).toBeInTheDocument();
  });

  it("supports a dedicated no-search-results state and clear-search action", () => {
    const onClearSearch = vi.fn();
    render(<DataTable caption="Customers" columns={columns} rows={[]} state="no-search-results" onClearSearch={onClearSearch} />);
    expect(screen.getByRole("status")).toHaveTextContent("No search results");
    fireEvent.click(screen.getByRole("button", { name: "Clear search" }));
    expect(onClearSearch).toHaveBeenCalledOnce();
  });

  it("prioritizes loading and retrieval failure over empty data", () => {
    const { rerender } = render(<DataTable caption="Houses" columns={columns} rows={[]} loading />);
    expect(screen.getByRole("status")).toHaveTextContent("Loading records");

    rerender(<DataTable caption="Houses" columns={columns} rows={[]} error={new Error("Offline")} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Could not load records");
  });

  it("shows retrieval failures from the farm context and retries through the app", () => {
    const retryData = vi.fn();
    render(
      <FarmManagerContext.Provider value={{ dataLoadError: "The local backend is unavailable.", retryData }}>
        <DataTable caption="Houses" columns={columns} rows={[]} />
      </FarmManagerContext.Provider>
    );
    expect(screen.getByRole("alert")).toHaveTextContent("The local backend is unavailable.");
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(retryData).toHaveBeenCalledOnce();
  });

  it("keeps previously loaded rows visible when a later read fails", () => {
    render(<DataTable caption="Houses" columns={columns} rows={[{ id: "h1", name: "House A" }]} error="Refresh failed." />);
    expect(screen.getByRole("alert")).toHaveTextContent("Refresh failed.");
    expect(screen.getByRole("cell", { name: "House A" })).toBeInTheDocument();
  });

  it("does not replace valid rows with an empty state", () => {
    render(<DataTable caption="Houses" columns={columns} rows={[{ id: "h1", name: "House A" }]} />);
    expect(screen.getByRole("cell", { name: "House A" })).toBeInTheDocument();
    expect(screen.queryByText("Nothing here yet")).not.toBeInTheDocument();
  });
});
