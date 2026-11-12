export default function DraftStatus({ draft, onDiscard, onReview }) {
  if (!draft?.hasDraft && !draft?.saving) return null;

  const savedAt = draft.savedAt
    ? new Date(draft.savedAt).toLocaleString("en-NG", { dateStyle: "medium", timeStyle: "short" })
    : null;

  return (
    <div className="subsection draft-status" role="group" aria-label="Form draft status">
      <p className="muted" role="status">
        {draft.saving ? "Saving draft…" : savedAt ? `Draft saved ${savedAt}` : "Draft available"}
      </p>
      {draft.stale && (
        <div className="notice-banner" role="alert">
          <strong>Review this draft before submitting.</strong>
          <span>One or more referenced farm records changed or are no longer available. Check the selected batch, house, feed type, supplier, and customer before continuing.</span>
          <button type="button" className="secondary-button" onClick={onReview}>I reviewed these details</button>
        </div>
      )}
      <button type="button" className="secondary-button" onClick={onDiscard}>Discard draft</button>
    </div>
  );
}
