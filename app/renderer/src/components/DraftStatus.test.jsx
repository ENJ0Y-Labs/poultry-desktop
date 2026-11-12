import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DraftStatus from "./DraftStatus.jsx";

describe("DraftStatus", () => {
  it("shows the saved state and last-saved time", () => {
    render(<DraftStatus draft={{ hasDraft: true, savedAt: "2026-11-12T10:00:00.000Z" }} onDiscard={vi.fn()} />);
    expect(screen.getByRole("status").textContent).toContain("Draft saved");
    expect(screen.getByRole("button", { name: "Discard draft" })).toBeTruthy();
  });

  it("requires an explicit review when referenced records are stale", () => {
    const onReview = vi.fn();
    render(<DraftStatus draft={{ hasDraft: true, stale: true, savedAt: "2026-11-12T10:00:00.000Z" }} onReview={onReview} onDiscard={vi.fn()} />);
    expect(screen.getByRole("alert").textContent).toContain("Review this draft before submitting");
    fireEvent.click(screen.getByRole("button", { name: "I reviewed these details" }));
    expect(onReview).toHaveBeenCalledOnce();
  });

  it("allows deliberate discard", () => {
    const onDiscard = vi.fn();
    render(<DraftStatus draft={{ hasDraft: true, savedAt: "2026-11-12T10:00:00.000Z" }} onDiscard={onDiscard} />);
    fireEvent.click(screen.getByRole("button", { name: "Discard draft" }));
    expect(onDiscard).toHaveBeenCalledOnce();
  });

  it("does not render when no draft exists", () => {
    const { container } = render(<DraftStatus draft={null} onDiscard={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });
});
