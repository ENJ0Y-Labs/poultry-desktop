import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { Modal } from "./Modal.jsx";

describe("Modal", () => {
  it("moves focus into the dialog and restores focus to the opener", async () => {
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>Open modal</button>
          <Modal open={open} onClose={() => setOpen(false)} title="Create record">
            <input aria-label="Record name" />
          </Modal>
        </>
      );
    }

    render(<Harness />);
    const opener = screen.getByRole("button", { name: "Open modal" });
    opener.focus();
    fireEvent.click(opener);
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Record name" })).toHaveFocus());
    fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(opener).toHaveFocus();
  });

  it("requires an explicit discard choice when the form is dirty", () => {
    const onClose = vi.fn();
    const onDiscard = vi.fn();
    render(<Modal open onClose={onClose} onDiscard={onDiscard} title="Edit record" isDirty>
      <input aria-label="Name" />
    </Modal>);

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.getByText("Discard unsaved changes?")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Keep editing" }));
    expect(screen.queryByText("Discard unsaved changes?")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
    expect(screen.getByText("Discard unsaved changes?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Discard changes" }));
    expect(onDiscard).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("keeps keyboard focus inside the dialog", () => {
    render(<Modal open onClose={() => {}} title="Keyboard dialog">
      <button type="button">First action</button>
      <button type="button">Last action</button>
    </Modal>);
    const close = screen.getByRole("button", { name: "Close dialog" });
    const last = screen.getByRole("button", { name: "Last action" });
    last.focus();
    fireEvent.keyDown(last, { key: "Tab" });
    expect(close).toHaveFocus();
    close.focus();
    fireEvent.keyDown(close, { key: "Tab", shiftKey: true });
    expect(last).toHaveFocus();
  });

  it("does not close while a submission is loading", () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} title="Save record" loading>
      <input aria-label="Name" />
    </Modal>);

    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.mouseDown(document.querySelector(".pomas-modal-backdrop"));
    fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("renders an accessible error message", () => {
    render(<Modal open onClose={() => {}} title="Save record" error="The server rejected this record.">
      <p>Form content</p>
    </Modal>);
    expect(screen.getByRole("alert")).toHaveTextContent("The server rejected this record.");
  });
});
