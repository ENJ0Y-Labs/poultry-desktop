import { useEffect, useRef, useState } from "react";
import "./RowActionsMenu.css";

/**
 * Configurable row menu. Callers own domain rules and must still rely on the
 * backend to authorize and validate every mutation.
 *
 * Action shape:
 * { id, label, onSelect(row), visible?, disabled?, disabledReason?, destructive? }
 */
export default function RowActionsMenu({ row, actions = [], label = "Record actions" }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const triggerRef = useRef(null);
  const menuRef = useRef(null);

  const availableActions = actions.filter(action => action && action.visible !== false);

  useEffect(() => {
    if (!open) return undefined;

    function handlePointerDown(event) {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    }

    function handleKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        setOpen(false);
        triggerRef.current?.focus();
        return;
      }

      if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;
      const items = [...(menuRef.current?.querySelectorAll('[role="menuitem"]:not(:disabled)') || [])];
      if (!items.length) return;
      event.preventDefault();
      const currentIndex = items.indexOf(document.activeElement);
      const nextIndex = event.key === "Home" ? 0
        : event.key === "End" ? items.length - 1
          : event.key === "ArrowDown" ? (currentIndex + 1 + items.length) % items.length
            : (currentIndex <= 0 ? items.length - 1 : currentIndex - 1);
      items[nextIndex].focus();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    const firstEnabled = menuRef.current?.querySelector('[role="menuitem"]:not(:disabled)');
    firstEnabled?.focus();

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  if (!availableActions.length) return null;

  return (
    <div className="pomas-row-actions" ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        className="pomas-row-actions__trigger"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(value => !value)}
      >
        <span aria-hidden="true">⋯</span>
      </button>
      {open && (
        <div className="pomas-row-actions__menu" role="menu" aria-label={label} ref={menuRef}>
          {availableActions.map(action => (
            <button
              key={action.id}
              type="button"
              role="menuitem"
              className={action.destructive ? "pomas-row-actions__item is-destructive" : "pomas-row-actions__item"}
              disabled={Boolean(action.disabled)}
              title={action.disabled ? action.disabledReason || undefined : undefined}
              aria-label={action.disabled && action.disabledReason ? `${action.label}. ${action.disabledReason}` : action.label}
              onClick={() => {
                if (action.disabled) return;
                setOpen(false);
                action.onSelect?.(row);
              }}
            >
              <span>{action.label}</span>
              {action.disabled && action.disabledReason && <small>{action.disabledReason}</small>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
