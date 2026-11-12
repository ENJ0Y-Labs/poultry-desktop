export default function DraftStatus({ draft, onDiscard, onReview }) {
  if (!draft?.hasDraft && !draft?.saving) return null;

  const savedAt = draft.savedAt
    ? new Date(draft.savedAt).toLocaleString("en-NG", { dateStyle: "medium", timeStyle: "short" })
    : null;

  return (
    <div className="subsection draft-status" role="group" aria-label="Form draft status">
      <p className="muted" role="status">
        {draft.saving ? "Saving draft…" : draft.saveError ? "Draft could not be saved. Keep this window open and try again." : savedAt ? `Draft saved ${savedAt}` : "Draft available"}
      </p>
      {draft.stale && (
        <div className="notice-banner" role="alert">
          <strong>Review this draft before submitting.</strong>
          <span>These references changed or are no longer available: {(draft.staleReferences || []).map(item => ({ batch: "batch", house: "house / pen", feedType: "feed type", supplier: "supplier", customer: "customer" }[item] || item)).join(", ") || "farm records"}. Check the form values before continuing.</span>
          <button type="button" className="secondary-button" onClick={onReview}>I reviewed these details</button>
        </div>
      )}
      <button type="button" className="secondary-button" onClick={onDiscard}>Discard draft</button>
    </div>
  );
}
