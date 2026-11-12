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

  it("contains the former dashboard shortcuts in one global action menu", async () => {
    render(<QuickActionsMenu
      activeBatch={{ id: "batch-1", type: "LAYER", status: "ACTIVE" }}
      hasFeedTypes
      hasApplicableFeed
      onNavigate={vi.fn()}
    />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));

    for (const label of ["Mortality", "Egg collection", "Feed usage", "Sale", "Expense"]) {
      expect(await screen.findByRole("button", { name: new RegExp(label) })).toBeInTheDocument();
    }
  });

  it("hides batch-specific and incompatible actions when no active batch exists", async () => {
    render(<QuickActionsMenu hasFeedTypes={false} hasInventoryItems={false} onNavigate={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));
    expect(await screen.findByRole("button", { name: /Expense/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Inventory item/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mortality/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Egg collection/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Feed purchase/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Stock movement/ })).not.toBeInTheDocument();
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
  it("opens when the central shortcut manager dispatches its event", async () => {
    render(<QuickActionsMenu activeBatch={{ id: "batch-1", type: "LAYER", status: "ACTIVE" }} />);
    window.dispatchEvent(new Event("pomas:open-quick-actions"));
    expect(await screen.findByRole("dialog", { name: "Quick actions" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: /Daily record/ })).toHaveFocus());
  });

  it.each([
    ["Daily record", "#daily-work/daily-record/batchId=batch-1"],
    ["Mortality", "#daily-work/mortality/batchId=batch-1"],
    ["Culling", "#daily-work/culling/batchId=batch-1"],
    ["Feed usage", "#daily-work/daily-record/batchId=batch-1"],
    ["Egg collection", "#production/eggs/batchId=batch-1"],
    ["Vaccination", "#health/vaccination/batchId=batch-1"],
    ["Health event", "#health/health-record/batchId=batch-1"],
    ["Feed purchase", "#feed-stock/purchases"],
    ["Sale", "#production/eggs/batchId=batch-1"],
    ["Expense", "#sales-money/expenses/batchId=batch-1"],
    ["Inventory item", "#feed-stock/inventory"],
    ["Stock movement", "#feed-stock/inventory/batchId=batch-1"],
  ])("routes %s into its existing workflow", async (label, target) => {
    const onNavigate = vi.fn();
    render(<QuickActionsMenu
      activeBatch={{ id: "batch-1", type: "LAYER", status: "ACTIVE" }}
      hasFeedTypes
      hasApplicableFeed
      hasInventoryItems
      onNavigate={onNavigate}
    />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));
    fireEvent.click(await screen.findByRole("button", { name: new RegExp(label) }));
    expect(onNavigate).toHaveBeenCalledWith(target);
  });

  it("hides batch actions when the supplied batch is closed", async () => {
    render(<QuickActionsMenu activeBatch={{ id: "closed-1", type: "LAYER", status: "SOLD" }} />);
    fireEvent.click(screen.getByRole("button", { name: /Quick actions/ }));
    expect(await screen.findByRole("button", { name: /Expense/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mortality/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Egg collection/ })).not.toBeInTheDocument();
  });

});
