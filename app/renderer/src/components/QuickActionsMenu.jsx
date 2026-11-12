import { useEffect, useMemo, useRef, useState } from "react";
import "./QuickActionsMenu.css";

const actionGroups = [
  {
    label: "Daily work",
    actions: [
      { id: "daily-record", label: "Daily record", description: "Record notes and water", page: "daily-work", tab: "daily-record", requiresBatch: true },
      { id: "mortality", label: "Mortality", description: "Record birds that died", page: "daily-work", tab: "mortality", requiresBatch: true },
      { id: "culling", label: "Culling", description: "Record birds removed", page: "daily-work", tab: "culling", requiresBatch: true },
      { id: "feed-usage", label: "Feed usage", description: "Record feed given to a batch", page: "daily-work", tab: "daily-record", requiresBatch: true, requiresApplicableFeed: true },
    ],
  },
  {
    label: "Flock & health",
    actions: [
      { id: "egg-collection", label: "Egg collection", description: "Record layer egg production", page: "production", tab: "eggs", requiresBatch: true, requiresLayer: true },
      { id: "vaccination", label: "Vaccination", description: "Record a vaccine dose", page: "health", tab: "vaccination", requiresBatch: true },
      { id: "health-event", label: "Health event", description: "Record an observation or illness", page: "health", tab: "health-record", requiresBatch: true },
    ],
  },
  {
    label: "Finance & stock",
    actions: [
      { id: "feed-purchase", label: "Feed purchase", description: "Add a feed stock purchase", page: "feed-stock", tab: "purchases", requiresFeedType: true },
      { id: "sale", label: "Sale", description: "Record a batch sale", page: "production", tab: "eggs", requiresBatch: true, saleRoute: true },
      { id: "expense", label: "Expense", description: "Record a farm or batch expense", page: "sales-money", tab: "expenses", supportsBatchContext: true },
      { id: "inventory-item", label: "Inventory item", description: "Add a stock item", page: "feed-stock", tab: "inventory" },
      { id: "stock-movement", label: "Stock movement", description: "Receive or issue existing stock", page: "feed-stock", tab: "inventory", requiresInventoryItem: true, supportsBatchContext: true },
    ],
  },
];

export default function QuickActionsMenu({
  activeBatch = null,
  hasFeedTypes = false,
  hasApplicableFeed = false,
  hasInventoryItems = false,
  onNavigate,
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const triggerRef = useRef(null);
  const itemRefs = useRef([]);

  const groups = useMemo(() => actionGroups.map(group => ({
    ...group,
    actions: group.actions.filter(action => {
      if (action.requiresBatch && !(activeBatch?.status === "ACTIVE" && activeBatch?.id)) return false;
      if (action.requiresLayer && (activeBatch?.status !== "ACTIVE" || activeBatch?.type !== "LAYER")) return false;
      if (action.requiresApplicableFeed && !hasApplicableFeed) return false;
      if (action.requiresFeedType && !hasFeedTypes) return false;
      if (action.requiresInventoryItem && !hasInventoryItems) return false;
      return true;
    }),
  })).filter(group => group.actions.length > 0), [activeBatch, hasFeedTypes, hasApplicableFeed, hasInventoryItems]);

  const visibleActions = groups.flatMap(group => group.actions);
  const hasActiveBatch = activeBatch?.status === "ACTIVE" && Boolean(activeBatch?.id);

  useEffect(() => {
    if (open) itemRefs.current[0]?.focus();
  }, [open]);

  useEffect(() => {
    function handleGlobalShortcut(event) {
      if (event.ctrlKey && !event.altKey && !event.shiftKey && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setOpen(true);
      }
    }
    window.addEventListener("keydown", handleGlobalShortcut);
    return () => window.removeEventListener("keydown", handleGlobalShortcut);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    function closeOnOutsidePointer(event) {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    }
    document.addEventListener("pointerdown", closeOnOutsidePointer);
    return () => document.removeEventListener("pointerdown", closeOnOutsidePointer);
  }, [open]);

  function handleMenuKeyDown(event) {
    if (event.key === "Escape") {
      event.preventDefault();
      setOpen(false);
      triggerRef.current?.focus();
      return;
    }
    const currentIndex = itemRefs.current.indexOf(document.activeElement);
    let nextIndex = null;
    if (event.key === "ArrowDown") nextIndex = (currentIndex + 1) % visibleActions.length;
    if (event.key === "ArrowUp") nextIndex = (currentIndex - 1 + visibleActions.length) % visibleActions.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = visibleActions.length - 1;
    if (nextIndex !== null && visibleActions.length > 0) {
      event.preventDefault();
      itemRefs.current[nextIndex]?.focus();
    }
  }

  function selectAction(action) {
    const targetTab = action.saleRoute && activeBatch?.type !== "LAYER" ? "broiler" : action.tab;
    const context = (action.requiresBatch || action.supportsBatchContext) && hasActiveBatch
      ? `/batchId=${encodeURIComponent(activeBatch.id)}`
      : "";
    onNavigate?.(`#${action.page}/${targetTab}${context}`);
    setOpen(false);
  }

  return (
    <div className="global-quick-actions" ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        className="quick-actions-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls="quick-actions-panel"
        onClick={() => setOpen(value => !value)}
        onKeyDown={event => {
          if (event.key === "ArrowDown" && !open) {
            event.preventDefault();
            setOpen(true);
          }
        }}
      >
        <span aria-hidden="true">+</span>
        Quick actions
        <kbd>Ctrl K</kbd>
      </button>
      {open && (
        <div
          className="quick-actions-panel"
          id="quick-actions-panel"
          role="dialog"
          aria-label="Quick actions"
          onKeyDown={handleMenuKeyDown}
        >
          <div className="quick-actions-heading">
            <div>
              <strong>Quick actions</strong>
              <p>Start a farm task without leaving your current workflow.</p>
            </div>
            <button type="button" className="quick-actions-close" aria-label="Close quick actions" onClick={() => {
              setOpen(false);
              triggerRef.current?.focus();
            }}>×</button>
          </div>
          {groups.length > 0 ? groups.map(group => (
            <section className="quick-actions-group" aria-label={group.label} key={group.label}>
              <h3>{group.label}</h3>
              <div className="quick-actions-list">
                {group.actions.map(action => {
                  const index = visibleActions.findIndex(item => item.id === action.id);
                  return (
                    <button
                      key={action.id}
                      ref={element => { itemRefs.current[index] = element; }}
                      type="button"
                      className="quick-action-item"
                      onClick={() => selectAction(action)}
                    >
                      <span className="quick-action-copy">
                        <strong>{action.label}</strong>
                        <small>{action.description}</small>
                      </span>
                      <span className="quick-action-arrow" aria-hidden="true">↗</span>
                    </button>
                  );
                })}
              </div>
            </section>
          )) : <p className="quick-actions-empty">No quick actions are available yet. Set up a batch, feed type, or stock item first.</p>}
          <p className="quick-actions-hint"><kbd>↑</kbd><kbd>↓</kbd> Navigate <kbd>Enter</kbd> Open <kbd>Esc</kbd> Close</p>
        </div>
      )}
    </div>
  );
}
