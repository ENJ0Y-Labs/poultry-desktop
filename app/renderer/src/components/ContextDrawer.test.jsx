import React, { useState } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ContextDrawer from "./ContextDrawer.jsx";

describe("ContextDrawer", () => {
  it("opens as an accessible dialog, traps focus and restores focus on close", async () => {
    function Harness() {
      const [open, setOpen] = useState(false);
      return <>
        <button type="button" onClick={() => setOpen(true)}>Inspect batch</button>
        <ContextDrawer open={open} onClose={() => setOpen(false)} title="Batch B-001">
          <button type="button">View records</button>
          <button type="button">Last action</button>
        </ContextDrawer>
      </>;
    }
    expect(ContextDrawer).toBeDefined();
    render(React.createElement(Harness));
    const opener = screen.getByRole("button", { name: "Inspect batch" });
    opener.focus();
    fireEvent.click(opener);
    const drawer = await screen.findByRole("dialog", { name: "Batch B-001" });
    await waitFor(() => expect(withinCloseButton(drawer)).toHaveFocus());
    const last = screen.getByRole("button", { name: "Last action" });
    last.focus();
    fireEvent.keyDown(last, { key: "Tab" });
    expect(withinCloseButton(drawer)).toHaveFocus();
    fireEvent.click(withinCloseButton(drawer));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(opener).toHaveFocus();
  });

  it("asks before discarding dirty edits and keeps editing when requested", () => {
    const onClose = vi.fn();
    const onDiscard = vi.fn();
    render(<ContextDrawer open onClose={onClose} onDiscard={onDiscard} title="House 1" mode="editable" isDirty>
      <input aria-label="House notes" />
    </ContextDrawer>);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.getByText("Discard unsaved changes?")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Keep editing" }));
    expect(screen.queryByText("Discard unsaved changes?")).not.toBeInTheDocument();
    fireEvent.mouseDown(document.querySelector(".pomas-drawer-backdrop"));
    expect(screen.getByText("Discard unsaved changes?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Discard changes" }));
    expect(onDiscard).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("prevents dismissal while loading and announces errors", () => {
    const onClose = vi.fn();
    render(<ContextDrawer open onClose={onClose} title="Saving" loading error="Unable to save.">
      <p>Details</p>
    </ContextDrawer>);
    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.mouseDown(document.querySelector(".pomas-drawer-backdrop"));
    fireEvent.click(screen.getByRole("button", { name: "Close drawer" }));
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Unable to save.");
  });
});

function withinCloseButton(drawer) {
  return drawer.querySelector(".pomas-drawer-close");
}
