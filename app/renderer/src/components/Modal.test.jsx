import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Modal } from "./Modal.jsx";

describe("Modal", () => {
  it("moves focus into the dialog and restores focus to the opener", async () => {
    const onClose = vi.fn();
    render(
      <>
        <button type="button">Open modal</button>
        <Modal open onClose={onClose} title="Create record">
          <input aria-label="Record name" />
        </Modal>
      </>
    );

    const opener = screen.getByRole("button", { name: "Open modal" });
    opener.focus();
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Record name" })).toHaveFocus());
    fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("requires an explicit discard choice when the form is dirty", () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} title="Edit record" isDirty>
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
    expect(onClose).toHaveBeenCalledTimes(1);
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
