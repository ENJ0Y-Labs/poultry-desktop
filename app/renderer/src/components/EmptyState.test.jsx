import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
// eslint-disable-next-line no-unused-vars
import EmptyState from "./EmptyState.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "./DataTable.jsx";

afterEach(() => cleanup());

describe("EmptyState", () => {
  it.each([
    ["no-records", "Nothing here yet", "status"],
    ["no-results", "No matching records", "status"],
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

  it("prioritizes loading and retrieval failure over empty data", () => {
    const { rerender } = render(<DataTable caption="Houses" columns={columns} rows={[]} loading />);
    expect(screen.getByRole("status")).toHaveTextContent("Loading records");

    rerender(<DataTable caption="Houses" columns={columns} rows={[]} error={new Error("Offline")} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Could not load records");
  });

  it("does not replace valid rows with an empty state", () => {
    render(<DataTable caption="Houses" columns={columns} rows={[{ id: "h1", name: "House A" }]} />);
    expect(screen.getByRole("cell", { name: "House A" })).toBeInTheDocument();
    expect(screen.queryByText("Nothing here yet")).not.toBeInTheDocument();
  });
});
