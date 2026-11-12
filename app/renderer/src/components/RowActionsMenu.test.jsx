// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import RowActionsMenu from "./RowActionsMenu.jsx";

afterEach(() => cleanup());

describe("RowActionsMenu", () => {
  const row = { id: "house-1", name: "House 1" };

  it("renders no trigger when every action is hidden", () => {
    render(<RowActionsMenu row={row} actions={[{ id: "edit", label: "Edit", visible: false }]} />);
    expect(screen.queryByRole("button", { name: "Record actions" })).toBeNull();
  });

  it("shows configured actions and passes the selected row to the handler", () => {
    const onSelect = vi.fn();
    render(<RowActionsMenu row={row} actions={[{ id: "view", label: "View details", onSelect }]} />);

    fireEvent.click(screen.getByRole("button", { name: "Record actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "View details" }));

    expect(onSelect).toHaveBeenCalledOnce();
    expect(onSelect).toHaveBeenCalledWith(row);
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("prevents disabled actions and explains why they are unavailable", () => {
    const onSelect = vi.fn();
    render(<RowActionsMenu row={row} actions={[{
      id: "edit", label: "Edit", onSelect, disabled: true, disabledReason: "Posted records cannot be edited",
    }]} />);

    fireEvent.click(screen.getByRole("button", { name: "Record actions" }));
    const item = screen.getByRole("menuitem", { name: /Edit\. Posted records cannot be edited/ });
    expect(item).toBeDisabled();
    expect(item).toHaveTextContent("Posted records cannot be edited");
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("supports Escape and arrow-key navigation", () => {
    render(<RowActionsMenu row={row} actions={[
      { id: "view", label: "View" },
      { id: "edit", label: "Edit" },
    ]} />);

    const trigger = screen.getByRole("button", { name: "Record actions" });
    fireEvent.click(trigger);
    expect(screen.getByRole("menuitem", { name: "View" })).toHaveFocus();

    fireEvent.keyDown(document, { key: "ArrowDown" });
    expect(screen.getByRole("menuitem", { name: "Edit" })).toHaveFocus();

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("menu")).toBeNull();
    expect(trigger).toHaveFocus();
  });
});
