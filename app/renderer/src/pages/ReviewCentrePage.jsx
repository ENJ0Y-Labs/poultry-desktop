import { useCallback, useEffect, useState } from "react";
import { reportAttachmentApi, reportReviewApi } from "../services/api.js";

const statuses = ["SUBMITTED", "PENDING_REVIEW", "FLAGGED", "BLOCKED", "APPROVED", "MERGED"];

function label(value) {
  return String(value || "").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
}

export default function ReviewCentrePage({ currentUser }) {
  const [queue, setQueue] = useState([]);
  const [managers, setManagers] = useState([]);
  const [status, setStatus] = useState("");
  const [reportType, setReportType] = useState("");
  const [selected, setSelected] = useState(null);
  const [assignment, setAssignment] = useState("");
  const [comment, setComment] = useState("");
  const [reason, setReason] = useState("");
  const [overrideReasons, setOverrideReasons] = useState({});
  const [correctionReason, setCorrectionReason] = useState("");
  const [correctionSampleQuantity, setCorrectionSampleQuantity] = useState("");
  const [correctionTotalWeightKg, setCorrectionTotalWeightKg] = useState("");
  const [correctionNotes, setCorrectionNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refreshQueue = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [rows, reviewers] = await Promise.all([
        reportReviewApi.queue({ status, reportType }),
        reportReviewApi.managers(),
      ]);
      setQueue(rows);
      setManagers(reviewers);
      if (selected?.id) {
        const refreshed = await reportReviewApi.get(selected.id);
        setSelected(refreshed);
        setAssignment(refreshed.assignedManagerId || "");
      }
    } catch (err) { setError(err.message || "Review queue could not be loaded."); }
    finally { setLoading(false); }
  }, [status, reportType, selected?.id]);

  useEffect(() => { refreshQueue(); }, [refreshQueue]);

  async function openSubmission(row) {
    setError("");
    try {
      const detail = await reportReviewApi.get(row.id);
      setSelected(detail);
      setAssignment(detail.assignedManagerId || "");
      setReason("");
      setNotice("");
    } catch (err) { setError(err.message || "Report could not be opened."); }
  }

  async function assignReviewer(event) {
    event.preventDefault();
    if (!selected || !assignment) return;
    setSaving(true);
    setError("");
    try {
      const updated = await reportReviewApi.assign(selected.id, assignment);
      setSelected(updated);
      setNotice("Reviewer assignment recorded.");
      await refreshQueue();
    } catch (err) { setError(err.message || "Assignment failed."); }
    finally { setSaving(false); }
  }

  async function postComment(event) {
    event.preventDefault();
    if (!selected || !comment.trim()) return;
    setSaving(true);
    setError("");
    try {
      const updated = await reportReviewApi.comment(selected.id, comment.trim());
      setSelected(updated);
      setComment("");
      setNotice("Comment added to the report history.");
      await refreshQueue();
    } catch (err) { setError(err.message || "Comment could not be saved."); }
    finally { setSaving(false); }
  }

  async function authorizeWarning(check) {
    const overrideReason = (overrideReasons[check.id] || "").trim();
    if (!selected || !overrideReason) {
      setError("Enter a reason for this warning authorization.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const updated = await reportReviewApi.overrideWarning(selected.id, check.id, overrideReason);
      setSelected(updated);
      setOverrideReasons(current => ({ ...current, [check.id]: "" }));
      setNotice("Warning authorization recorded in the immutable review history.");
      await refreshQueue();
    } catch (err) { setError(err.message || "Warning could not be authorized."); }
    finally { setSaving(false); }
  }

  async function downloadEvidence(attachment) {
    try {
      const blob = await reportAttachmentApi.download(selected.id, attachment.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = attachment.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) { setError(err.message || "Evidence download failed."); }
  }

  async function mergeApproved() {
    if (!selected) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const operationKey = globalThis.crypto?.randomUUID?.()
        || "merge-" + Date.now() + "-" + Math.random().toString(36).slice(2);
      const updated = await reportReviewApi.merge(selected.id, operationKey);
      setSelected(updated);
      setNotice("Approved report merged into official farm records. Provenance links have been saved.");
      await refreshQueue();
    } catch (err) { setError(err.message || "Report merge failed. Official records were not partially updated."); }
    finally { setSaving(false); }
  }

  async function correctMergedWeight(event) {
    event.preventDefault();
    if (!selected || !correctionReason.trim() || !correctionSampleQuantity || !correctionTotalWeightKg) {
      setError("Provide a correction reason, sample quantity and total sample weight.");
      return;
    }
    const version = selected.versions?.find(item => item.version === (selected.submittedVersion || selected.currentVersion));
    const originalPayload = version?.payload || {};
    const recordDate = originalPayload.recordDate || originalPayload.eventDate;
    if (!recordDate) {
      setError("The original report has no usable measurement date. The correction was not submitted.");
      return;
    }
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await reportReviewApi.correctWeight(selected.id, {
        reason: correctionReason.trim(),
        replacement: {
          recordDate,
          sampleQuantity: Number(correctionSampleQuantity),
          totalWeightKg: correctionTotalWeightKg,
          notes: correctionNotes.trim() || null,
        },
      });
      const updated = await reportReviewApi.get(selected.id);
      setSelected(updated);
      setCorrectionReason("");
      setCorrectionSampleQuantity("");
      setCorrectionTotalWeightKg("");
      setCorrectionNotes("");
      setNotice("The original weight record was preserved and reversed. A validated replacement and correction provenance were recorded atomically.");
      await refreshQueue();
    } catch (err) {
      setError(err.message || "The post-merge correction failed. The original official record was not partially changed.");
    } finally {
      setSaving(false);
    }
  }

  async function decide(decision) {
    if (!selected) return;
    if (decision !== "APPROVED" && !reason.trim()) {
      setError("Enter a reason before returning or rejecting this report.");
      return;
    }
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await reportReviewApi.decide(selected.id, decision, reason.trim() || null);
      setSelected(updated);
      setReason("");
      setNotice(decision === "APPROVED"
        ? "This exact report version is approved. It has not changed official farm records."
        : decision === "REJECTED"
          ? "Report rejected. Official farm records remain unchanged."
          : "Report returned to its author for correction.");
      await refreshQueue();
    } catch (err) { setError(err.message || "Review decision failed."); }
    finally { setSaving(false); }
  }

  const ownSubmission = selected?.authorId && selected.authorId === currentUser?.id;
  const decisionAllowed = selected
    && !ownSubmission
    && ["SUBMITTED", "PENDING_REVIEW", "FLAGGED", "BLOCKED"].includes(selected.lifecycleStatus || selected.status);
  const selectedVersion = selected?.submittedVersion || selected?.currentVersion;
  const selectedPayload = selected?.versions?.find(item => item.version === selectedVersion)?.payload || {};
  const weightCorrectionAlreadyApplied = (selected?.history || []).some(item => item.event === "POST_MERGE_CORRECTED");

  return (
    <div className="review-centre-page">
      <section className="card full">
        <div className="section-head">
          <div><p className="eyebrow">MANAGER WORKSPACE</p><h2>Review Centre</h2><p className="muted">Review proposed reports. Approval is separate from merging into official records.</p></div>
          <button type="button" className="secondary-button" onClick={refreshQueue} disabled={loading}>Refresh queue</button>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="form-success" role="status">{notice}</p>}
        <div className="review-filters">
          <label>Status
            <select value={status} onChange={event => setStatus(event.target.value)}>
              <option value="">All pending reviews</option>
              {statuses.map(value => <option key={value} value={value}>{label(value)}</option>)}
            </select>
          </label>
          <label>Report type
            <select value={reportType} onChange={event => setReportType(event.target.value)}>
              <option value="">All report types</option>
              {["DAILY_OPERATIONS","MORTALITY","CULLING","HEALTH_RECORD","DRUG_TREATMENT","VACCINATION","EGG_COLLECTION","WEIGHT_RECORD","FEED_USAGE","EXPENSE","BIRD_SALE","EGG_SALE"].map(value => <option key={value} value={value}>{label(value)}</option>)}
            </select>
          </label>
        </div>
        <div className="review-layout">
          <section className="review-queue">
            <h3>{status === "MERGED" ? "Merged reports" : "Waiting for review"} <span className="muted">{queue.length}</span></h3>
            {loading ? <p className="muted">Loading review queue…</p> : queue.length === 0
              ? <p className="muted">No reports match these filters.</p>
              : queue.map(row => <button type="button" className={selected?.id === row.id ? "review-queue-item selected" : "review-queue-item"} key={row.id} onClick={() => openSubmission(row)}>
                <span className="review-queue-item-top"><strong>{row.title}</strong><span className="status-pill">{label(row.lifecycleStatus || row.status)}</span></span>
                <span>{row.authorFullName || row.authorUsername || row.authorId}</span>
                <small>{label(row.reportType)} · v{row.submittedVersion || row.currentVersion} · {row.submittedAt || row.updatedAt}</small>
              </button>)}
          </section>
          <section className="review-detail">
            {!selected ? <p className="muted">Select a report to inspect its submitted version, checks, discussion and history.</p> : <>
              <div className="section-head"><div><p className="eyebrow">SUBMISSION {selected.id}</p><h3>{selected.title}</h3><p className="muted">{label(selected.reportType)} · {label(selected.lifecycleStatus || selected.status)} · submitted version {selected.submittedVersion || selected.currentVersion}</p></div><button type="button" className="text-button" onClick={() => setSelected(null)}>Close</button></div>
              {ownSubmission && <p className="form-warning" role="note">This is your own submission. You can inspect it, but cannot approve or reject it.</p>}
              <div className="review-meta-grid">
                <div><small>Author</small><strong>{selected.authorFullName || selected.authorUsername || selected.authorId}</strong></div>
                <div><small>Submitted</small><strong>{selected.submittedAt || "—"}</strong></div>
                <div><small>Assigned manager</small><strong>{selected.assignedManagerId || "Unassigned"}</strong></div>
              </div>
              <section className="review-payload"><h4>Submitted report snapshot</h4><pre>{JSON.stringify(selected.versions?.find(item => item.version === (selected.submittedVersion || selected.currentVersion))?.payload || {}, null, 2)}</pre></section>
              {(selected.attachments || []).length > 0 && <section className="review-evidence"><h4>Evidence attachments</h4>{selected.attachments.map(attachment => <article key={attachment.id}><strong>{attachment.fileName}</strong><span>{Math.round(attachment.fileSize / 1024)} KB · {attachment.mimeType}</span><button type="button" className="secondary-button" onClick={() => downloadEvidence(attachment)}>Download evidence</button></article>)}</section>}
              <section className="review-checks"><h4>Automated checks</h4>{(selected.checks || []).length === 0 ? <p className="muted">No automated checks have been recorded for this version yet.</p> : selected.checks.map(check => <article key={check.id}><span className={"check-outcome check-" + check.outcome.toLowerCase()}>{label(check.outcome)}</span><strong>{label(check.code)}</strong><p>{check.explanation}</p>
                {check.outcome === "WARNING" && check.overrideable && !(selected.history || []).some(event => event.event === "WARNING_OVERRIDDEN" && event.details?.checkId === check.id) && <div className="warning-override-form"><label htmlFor={"override-" + check.id}>Manager authorization reason</label><textarea id={"override-" + check.id} rows={2} maxLength={1000} value={overrideReasons[check.id] || ""} onChange={event => setOverrideReasons(current => ({ ...current, [check.id]: event.target.value }))} /><button type="button" className="secondary-button" disabled={saving || !(overrideReasons[check.id] || "").trim()} onClick={() => authorizeWarning(check)}>Authorize this warning</button></div>}
                {check.outcome === "WARNING" && (selected.history || []).some(event => event.event === "WARNING_OVERRIDDEN" && event.details?.checkId === check.id) && <small>Manager authorization recorded.</small>}
              </article>)}</section>
              <section className="review-assignment"><h4>Reviewer assignment</h4><form className="inline-form" onSubmit={assignReviewer}>
                <select aria-label="Assign manager" value={assignment} onChange={event => setAssignment(event.target.value)}>
                  <option value="">Choose a Manager</option>
                  {managers.map(manager => <option key={manager.id} value={manager.id}>{manager.fullName} ({manager.username})</option>)}
                </select>
                <button type="submit" className="secondary-button" disabled={saving || !assignment}>Assign</button>
              </form>
              </section>
              <section className="review-discussion"><h4>Discussion</h4>{(selected.comments || []).map(item => <article key={item.id}><strong>{item.authorName || "Farm user"}</strong><p>{item.body}</p><small>{item.createdAt}</small></article>)}
                <form onSubmit={postComment}><label htmlFor="manager-comment">Add review comment</label><textarea id="manager-comment" rows={3} maxLength={4000} value={comment} onChange={event => setComment(event.target.value)} /><button type="submit" className="secondary-button" disabled={saving || !comment.trim()}>Add comment</button></form>
              </section>
              <section className="review-decision"><h4>Review decision</h4>
                {decisionAllowed ? <>
                  <label htmlFor="review-reason">Reason for returning or rejecting (required for those decisions)</label>
                  <textarea id="review-reason" rows={3} maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} />
                  <div className="inline-form">
                    <button type="button" className="secondary-button" disabled={saving || !reason.trim()} onClick={() => decide("CHANGES_REQUESTED")}>Request changes</button>
                    <button type="button" className="danger-button" disabled={saving || !reason.trim()} onClick={() => decide("REJECTED")}>Reject report</button>
                    <button type="button" disabled={saving || (selected.lifecycleStatus || selected.status) === "BLOCKED"} onClick={() => decide("APPROVED")}>Approve version</button>
                  </div>
                  <p className="muted">Approval authorizes this exact version only. Official farm records remain unchanged until a separate merge succeeds.</p>
                </> : <p className="muted">No review decision is available for this report in its current state.</p>}
              </section>
              {((selected.lifecycleStatus || selected.status) === "APPROVED") && <section className="review-merge">
                <h4>Merge into official farm records</h4>
                <p className="muted">The system will re-run validation and call the existing farm services in one transaction. If any write fails, all writes from this merge are rolled back.</p>
                <button type="button" disabled={saving} onClick={mergeApproved}>{saving ? "Merging…" : "Merge approved report"}</button>
              </section>}
              {((selected.lifecycleStatus || selected.status) === "MERGED") && <section className="review-merge">
                <h4>Official records and correction history</h4>
                {(selected.links || []).map(link => <article key={link.id}><strong>{label(link.recordType)}</strong><span>{link.recordId}</span><small>Version {link.version} · {link.createdAt}</small></article>)}
                {selected.reportType === "WEIGHT_RECORD" && (selected.links || []).filter(link => link.recordType === "WEIGHT_RECORD").length >= 1 && <form className="review-correction-form" onSubmit={correctMergedWeight}>
                  <h4>Correct latest effective weight record</h4>
                  <p className="muted">Each correction preserves the previous row and creates a replacement through the existing production service. The next correction targets the latest effective replacement. Corrections are rejected when later measurements depend on the record. The original measurement date is retained: {selectedPayload.recordDate || selectedPayload.eventDate || "Unavailable"}.</p>
                  <label htmlFor="weight-correction-reason">Correction reason</label>
                  <textarea id="weight-correction-reason" rows={2} maxLength={500} value={correctionReason} onChange={event => setCorrectionReason(event.target.value)} required />
                  <label htmlFor="weight-correction-sample">Corrected birds sampled</label>
                  <input id="weight-correction-sample" type="number" min="1" step="1" value={correctionSampleQuantity} onChange={event => setCorrectionSampleQuantity(event.target.value)} required />
                  <label htmlFor="weight-correction-total">Corrected total sample weight (kg)</label>
                  <input id="weight-correction-total" type="number" min="0.001" step="0.001" value={correctionTotalWeightKg} onChange={event => setCorrectionTotalWeightKg(event.target.value)} required />
                  <label htmlFor="weight-correction-notes">Replacement notes</label>
                  <textarea id="weight-correction-notes" rows={2} maxLength={1000} value={correctionNotes} onChange={event => setCorrectionNotes(event.target.value)} />
                  <button type="submit" disabled={saving || !correctionReason.trim() || !correctionSampleQuantity || !correctionTotalWeightKg}>Record correction</button>
                </form>}
                {selected.reportType !== "WEIGHT_RECORD" && <p className="muted">Post-merge correction is not yet available for this report type. Its official record remains unchanged.</p>}
                {weightCorrectionAlreadyApplied && <p className="muted">Earlier corrections are preserved in the provenance history. The form targets the latest effective replacement, not the original row.</p>}
              </section>}
              <section className="review-history"><h4>Decision and activity history</h4>{(selected.decisions || []).map(item => <article key={item.id}><strong>{label(item.decision)} · version {item.version}</strong><p>{item.reason || "No reason supplied"}</p><small>{item.managerName} · {item.createdAt}</small></article>)}{(selected.history || []).map(item => <p key={item.id}><strong>{label(item.event)}</strong> · {item.occurredAt}</p>)}</section>
            </>}
          </section>
        </div>
      </section>
    </div>
  );
}
