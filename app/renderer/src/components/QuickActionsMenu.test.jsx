import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import QuickActionsMenu from "./QuickActionsMenu.jsx";

describe("QuickActionsMenu", () => {
  it("shows actions supported by the current active batch and available stock", async () => {
    render(<QuickActionsMenu
      activeBatch={{ id: "batch-1", type: "LAYER", status: "ACTIVE" }}
      hasFeedTypes
      hasApplicableFeed
      hasInventoryItems
      onNavigate={vi.fn()}
    />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));
    expect(await screen.findByRole("button", { name: /Daily record/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Egg collection/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Feed purchase/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Stock movement/ })).toBeInTheDocument();
  });

  it("hides batch-specific and incompatible actions when no active batch exists", async () => {
    render(<QuickActionsMenu hasFeedTypes={false} hasInventoryItems={false} onNavigate={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));
    expect(await screen.findByText(/No quick actions are available yet/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mortality/ })).not.toBeInTheDocument();
  });

  it("routes a batch-specific action with the active batch context", async () => {
    const onNavigate = vi.fn();
    render(<QuickActionsMenu activeBatch={{ id: "batch-1", type: "LAYER", status: "ACTIVE" }} onNavigate={onNavigate} />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));
    fireEvent.click(await screen.findByRole("button", { name: /Mortality/ }));
    expect(onNavigate).toHaveBeenCalledWith("#daily-work/mortality/batchId=batch-1");
  });

  it("routes sales to the existing workflow for the selected batch type", async () => {
    const onNavigate = vi.fn();
    render(<QuickActionsMenu activeBatch={{ id: "turkey-1", type: "TURKEY", status: "ACTIVE" }} onNavigate={onNavigate} />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));
    fireEvent.click(await screen.findByRole("button", { name: /^Sale/ }));
    expect(onNavigate).toHaveBeenCalledWith("#production/broiler/batchId=turkey-1");
  });

  it("supports arrow-key navigation and Escape focus restoration", async () => {
    render(<QuickActionsMenu activeBatch={{ id: "batch-1", type: "BROILER", status: "ACTIVE" }} />);
    const trigger = screen.getByRole("button", { name: /Quick actions/ });
    fireEvent.click(trigger);
    const firstAction = await screen.findByRole("button", { name: /Daily record/ });
    await waitFor(() => expect(firstAction).toHaveFocus());
    fireEvent.keyDown(firstAction, { key: "ArrowDown" });
    expect(screen.getByRole("button", { name: /Mortality/ })).toHaveFocus();
    fireEvent.keyDown(screen.getByRole("button", { name: /Mortality/ }), { key: "Escape" });
    expect(trigger).toHaveFocus();
    expect(screen.queryByRole("dialog", { name: "Quick actions" })).not.toBeInTheDocument();
  });
});
