import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import "./Modal.css";

const FOCUSABLE = [
  "a[href]", "button:not([disabled])", "input:not([disabled])",
  "select:not([disabled])", "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

export function Modal({
  open, onClose, title, description, icon, size = "medium",
  loading = false, error = "", isDirty = false, children, footer,
}) {
  const dialogRef = useRef(null);
  const previousFocusRef = useRef(null);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    previousFocusRef.current = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const frame = requestAnimationFrame(() => {
      const first = dialogRef.current?.querySelector(FOCUSABLE);
      (first || dialogRef.current)?.focus();
    });

    function onKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        if (loading) return;
        if (isDirty) setConfirmDiscard(true);
        else onClose?.();
        return;
      }
      if (event.key !== "Tab") return;
      const items = [...(dialogRef.current?.querySelectorAll(FOCUSABLE) || [])]
        .filter(item => item.getClientRects().length > 0);
      if (!items.length) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocusRef.current?.focus?.();
    };
  }, [open, isDirty, loading, onClose]);

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
      className="pomas-modal-backdrop"
      onMouseDown={event => {
        if (event.target !== event.currentTarget || loading) return;
        requestClose();
      }}
    >
      <section
        ref={dialogRef}
        className={`pomas-modal pomas-modal--${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="pomas-modal-title"
        aria-describedby={description ? "pomas-modal-description" : undefined}
        tabIndex={-1}
      >
        <header className="pomas-modal-header">
          <div className="pomas-modal-heading">
            {icon && <span className="pomas-modal-icon" aria-hidden="true">{icon}</span>}
            <div>
              <h2 id="pomas-modal-title">{title}</h2>
              {description && <p id="pomas-modal-description">{description}</p>}
            </div>
          </div>
          <button type="button" className="pomas-modal-close" aria-label="Close dialog" onClick={requestClose} disabled={loading}>×</button>
        </header>

        <div className="pomas-modal-content">
          {error && <div className="error" role="alert">{error}</div>}
          {children}
          {confirmDiscard && (
            <div className="pomas-modal-discard" role="alertdialog" aria-labelledby="pomas-discard-title">
              <strong id="pomas-discard-title">Discard unsaved changes?</strong>
              <p>Your entries have not been saved. Discarding will close this form and lose those changes.</p>
              <div className="pomas-modal-actions">
                <button type="button" className="secondary-button" onClick={() => setConfirmDiscard(false)}>Keep editing</button>
                <button type="button" className="danger-button" onClick={() => { setConfirmDiscard(false); onClose?.(); }}>Discard changes</button>
              </div>
            </div>
          )}
        </div>
        {footer && <footer className="pomas-modal-footer">{footer}</footer>}
        {loading && <div className="pomas-modal-loading" role="status" aria-live="polite">Saving…</div>}
      </section>
    </div>,
    document.body
  );
}

export function ModalForm({
  title, description, triggerLabel, triggerClassName = "primary-button",
  size = "medium", loading = false, error = "", children,
}) {
  const [open, setOpen] = useState(false);
  const [dirty, setDirty] = useState(false);

  function show() {
    setDirty(false);
    setOpen(true);
  }

  function close() {
    if (loading) return;
    setOpen(false);
    setDirty(false);
  }

  return (
    <>
      <button type="button" className={triggerClassName} onClick={show} disabled={loading}>{triggerLabel}</button>
      <Modal
        open={open}
        onClose={close}
        title={title}
        description={description}
        size={size}
        loading={loading}
        error={error}
        isDirty={dirty}
      >
        <div onInputCapture={() => setDirty(true)} onChangeCapture={() => setDirty(true)}>
          {children}
        </div>
      </Modal>
    </>
  );
}
