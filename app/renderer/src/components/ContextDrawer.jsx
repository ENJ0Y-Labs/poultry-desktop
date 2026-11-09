import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import "./ContextDrawer.css";

const FOCUSABLE = [
  "a[href]", "button:not([disabled])", "input:not([disabled])",
  "select:not([disabled])", "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

export default function ContextDrawer({
  open, onClose, title, description, size = "medium", mode = "read-only",
  loading = false, error = "", isDirty = false, onDiscard, children, footer,
}) {
  const drawerRef = useRef(null);
  const previousFocusRef = useRef(null);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const titleId = useId();
  const descriptionId = useId();
  const latestRef = useRef({ onClose, isDirty, loading });
  latestRef.current = { onClose, isDirty, loading };

  useEffect(() => {
    if (!open) return undefined;
    previousFocusRef.current = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const focusTimer = window.setTimeout(() => {
      const closeButton = drawerRef.current?.querySelector(".pomas-drawer-close");
      (closeButton || drawerRef.current)?.focus();
    }, 0);

    function onKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        if (latestRef.current.loading) return;
        if (latestRef.current.isDirty) setConfirmDiscard(true);
        else latestRef.current.onClose?.();
        return;
      }
      if (event.key !== "Tab") return;
      const items = [...(drawerRef.current?.querySelectorAll(FOCUSABLE) || [])]
        .filter(item => !item.hasAttribute("hidden") && item.getAttribute("aria-hidden") !== "true");
      if (!items.length) {
        event.preventDefault();
        drawerRef.current?.focus();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === drawerRef.current)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => {
      window.clearTimeout(focusTimer);
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocusRef.current?.focus?.();
    };
  }, [open]);

  useEffect(() => {
    if (!open) setConfirmDiscard(false);
  }, [open]);

  if (!open) return null;

  function requestClose() {
    if (loading) return;
    if (isDirty) setConfirmDiscard(true);
    else onClose?.();
  }

  return createPortal(
    <div
      className="pomas-drawer-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) requestClose();
      }}
    >
      <aside
        ref={drawerRef}
        className={`pomas-context-drawer pomas-context-drawer--${size}`}
        data-mode={mode}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
      >
        <header className="pomas-drawer-header">
          <div className="pomas-drawer-heading">
            <span className="pomas-drawer-kicker">{mode === "editable" ? "RECORD DETAILS · EDITABLE" : "RECORD DETAILS"}</span>
            <h2 id={titleId}>{title}</h2>
            {description && <p id={descriptionId}>{description}</p>}
          </div>
          <button type="button" className="pomas-drawer-close" aria-label="Close drawer" onClick={requestClose} disabled={loading}>×</button>
        </header>
        <div className="pomas-drawer-content">
          {error && <div className="error" role="alert">{error}</div>}
          {children}
          {confirmDiscard && (
            <div className="pomas-drawer-discard" role="alert" aria-labelledby={`${titleId}-discard`}>
              <strong id={`${titleId}-discard`}>Discard unsaved changes?</strong>
              <p>Your edits have not been saved. Choose whether to keep editing or discard them.</p>
              <div className="pomas-drawer-actions">
                <button type="button" className="secondary-button" onClick={() => setConfirmDiscard(false)}>Keep editing</button>
                <button type="button" className="danger-button" onClick={() => { setConfirmDiscard(false); onDiscard?.(); onClose?.(); }}>Discard changes</button>
              </div>
            </div>
          )}
        </div>
        {footer && <footer className="pomas-drawer-footer">{footer}</footer>}
        {loading && <div className="pomas-drawer-loading" role="status" aria-live="polite">Saving…</div>}
      </aside>
    </div>,
    document.body
  );
}

export function DrawerDetails({ fields = [] }) {
  return (
    <dl className="pomas-drawer-details">
      {fields.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value == null || value === "" ? "—" : String(value)}</dd>
        </div>
      ))}
    </dl>
  );
}
