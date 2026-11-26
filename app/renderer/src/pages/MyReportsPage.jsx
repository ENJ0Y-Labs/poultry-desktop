import { useEffect, useMemo, useState } from "react";
import { reportSubmissionApi } from "../services/api.js";
import { parseNairaToKobo } from "../utils/moneyInput.js";

const reportDefinitions = {
  DAILY_OPERATIONS: {
    label: "Daily operations",
    fields: [
      ["eventDate", "Observation date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["notes", "What happened today?", "textarea", true],
      ["waterNotes", "Water observations", "textarea", false],
    ],
  },
  MORTALITY: {
    label: "Mortality",
    fields: [
      ["eventDate", "Date observed", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["quantity", "Number of birds", "number", true],
      ["reason", "Suspected cause / reason", "text", false],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  CULLING: {
    label: "Culling",
    fields: [
      ["eventDate", "Event date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["quantity", "Number of birds", "number", true],
      ["reason", "Reason for culling", "text", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  HEALTH_RECORD: {
    label: "Health observation",
    fields: [
      ["eventDate", "Observation date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["conditionProblem", "Condition / problem", "text", true],
      ["description", "What did you observe?", "textarea", true],
      ["action", "Action taken", "textarea", false],
    ],
  },
  DRUG_TREATMENT: {
    label: "Drug / treatment",
    fields: [
      ["eventDate", "Treatment date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["drug", "Drug / product", "text", true],
      ["quantity", "Quantity used", "number", true],
      ["costNaira", "Cost (₦)", "money", false],
      ["reason", "Reason / dosage notes", "textarea", false],
    ],
  },
  VACCINATION: {
    label: "Vaccination",
    fields: [
      ["eventDate", "Vaccination date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["vaccine", "Vaccine", "text", true],
      ["dose", "Dose", "text", true],
      ["quantity", "Birds vaccinated", "number", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  EGG_COLLECTION: {
    label: "Egg collection",
    fields: [
      ["eventDate", "Collection date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["good", "Good eggs", "number", true],
      ["cracked", "Cracked eggs", "number", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  WEIGHT_RECORD: {
    label: "Weight record",
    fields: [
      ["eventDate", "Measurement date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["sampleQuantity", "Birds sampled", "number", true],
      ["totalWeightKg", "Total sample weight (kg)", "decimal", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  FEED_USAGE: {
    label: "Feed usage",
    fields: [
      ["eventDate", "Usage date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["feedName", "Feed type / name", "text", true],
      ["quantity", "Quantity used", "decimal", true],
      ["unit", "Unit", "text", true],
      ["reason", "Notes", "textarea", false],
    ],
  },
  EXPENSE: {
    label: "Farm expense",
    fields: [
      ["eventDate", "Expense date", "date", true],
      ["batchCode", "Batch code (optional)", "text", false],
      ["description", "Description", "text", true],
      ["amountNaira", "Amount (₦)", "money", true],
      ["category", "Category", "text", true],
    ],
  },
  BIRD_SALE: {
    label: "Bird sale",
    fields: [
      ["eventDate", "Sale date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["quantity", "Birds sold", "number", true],
      ["pricePerBirdNaira", "Price per bird (₦)", "money", true],
      ["customer", "Customer", "text", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  EGG_SALE: {
    label: "Egg sale",
    fields: [
      ["eventDate", "Sale date", "date", true],
      ["batchCode", "Batch code", "text", true],
      ["crates", "Crates sold", "decimal", true],
      ["pricePerCrateNaira", "Price per crate (₦)", "money", true],
      ["customer", "Customer", "text", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
};

function makeId() {
  return globalThis.crypto?.randomUUID?.() || "report-" + Date.now() + "-" + Math.random().toString(36).slice(2);
}

function today() {
  const date = new Date();
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

function emptyFields(type) {
  const values = {};
  for (const [key, , kind] of reportDefinitions[type].fields) {
    values[key] = kind === "date" ? today() : "";
  }
  return values;
}

function toPayload(type, values) {
  const payload = {};
  for (const [key, , kind] of reportDefinitions[type].fields) {
    const value = values[key] ?? "";
    if (value === "") continue;
    if (kind === "number") payload[key] = Number.parseInt(value, 10);
    else if (kind === "decimal") payload[key] = Number(value);
    else if (kind === "money") {
      const target = key === "costNaira" ? "costMinor"
        : key === "pricePerBirdNaira" ? "pricePerBirdMinor"
          : key === "pricePerCrateNaira" ? "pricePerCrateMinor" : "amountMinor";
      payload[target] = parseNairaToKobo(value);
    } else payload[key] = value;
  }
  return payload;
}

function fromPayload(type, payload = {}) {
  const values = emptyFields(type);
  for (const [key] of reportDefinitions[type].fields) {
    let value = payload[key];
    if (key === "costNaira") value = payload.costMinor == null ? "" : (Number(payload.costMinor) / 100).toFixed(2);
    if (key === "pricePerBirdNaira") value = payload.pricePerBirdMinor == null ? "" : (Number(payload.pricePerBirdMinor) / 100).toFixed(2);
    if (key === "pricePerCrateNaira") value = payload.pricePerCrateMinor == null ? "" : (Number(payload.pricePerCrateMinor) / 100).toFixed(2);
    if (key === "amountNaira") value = payload.amountMinor == null ? "" : (Number(payload.amountMinor) / 100).toFixed(2);
    if (value != null) values[key] = String(value);
  }
  return values;
}

function statusLabel(report) {
  return String(report.lifecycleStatus || report.status || "DRAFT").replaceAll("_", " ").toLowerCase()
    .replace(/\b\w/g, letter => letter.toUpperCase());
}

export default function MyReportsPage({ userId = "" }) {
  const storageKey = "pomas:report-submission-drafts:" + (userId || "local-user");
  const [serverReports, setServerReports] = useState([]);
  const [localDrafts, setLocalDrafts] = useState(() => {
    try {
      const parsed = JSON.parse(localStorage.getItem(storageKey) || "[]");
      return Array.isArray(parsed) ? parsed : [];
    } catch { return []; }
  });
  const [reportType, setReportType] = useState("MORTALITY");
  const [title, setTitle] = useState("");
  const [fields, setFields] = useState(() => emptyFields("MORTALITY"));
  const [editing, setEditing] = useState(null);
  const [selected, setSelected] = useState(null);
  const [comment, setComment] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedDefinition = reportDefinitions[reportType];
  const allReports = useMemo(() => [
    ...localDrafts.map(item => ({ ...item, localOnly: true, status: "LOCAL_DRAFT" })),
    ...serverReports,
  ].sort((a, b) => String(b.updatedAt || b.createdAt || "").localeCompare(String(a.updatedAt || a.createdAt || ""))), [localDrafts, serverReports]);

  function persistLocal(next) {
    setLocalDrafts(next);
    try { localStorage.setItem(storageKey, JSON.stringify(next)); }
    catch { setError("This device could not save the local draft. Check available storage before continuing."); }
  }

  async function refresh() {
    setLoading(true);
    setError("");
    try { setServerReports(await reportSubmissionApi.mine()); }
    catch (err) { setError(err.message || "Could not load submitted reports."); }
    finally { setLoading(false); }
  }

  useEffect(() => { refresh(); }, [userId]);
  useEffect(() => {
    try { localStorage.setItem(storageKey, JSON.stringify(localDrafts)); } catch { /* surfaced when saving */ }
  }, [localDrafts, storageKey]);

  function resetForm() {
    setEditing(null);
    setReportType("MORTALITY");
    setTitle("");
    setFields(emptyFields("MORTALITY"));
    setNotice("");
  }

  function saveLocalDraft() {
    const draft = {
      id: editing?.id || makeId(),
      serverId: editing?.serverId || null,
      idempotencyKey: editing?.idempotencyKey || makeId(),
      reportType,
      title: title.trim(),
      fields,
      updatedAt: new Date().toISOString(),
      basePayload: editing?.basePayload || null,
    };
    const next = [...localDrafts.filter(item => item.id !== draft.id), draft];
    persistLocal(next);
    setEditing(draft);
    setNotice("Draft saved on this device. It will not be submitted automatically.");
    return draft;
  }

  function openLocalDraft(draft) {
    setEditing(draft);
    setReportType(draft.reportType);
    setTitle(draft.title || "");
    setFields({ ...emptyFields(draft.reportType), ...draft.fields });
    setSelected(null);
    setError("");
    setNotice("Local draft reopened.");
  }

  async function openServerReport(report) {
    setError("");
    try {
      const detail = await reportSubmissionApi.get(report.id);
      setSelected(detail);
      if (["DRAFT", "CHANGES_REQUESTED"].includes(detail.lifecycleStatus || detail.status)) {
        setEditing({
          id: "server-" + detail.id,
          serverId: detail.id,
          idempotencyKey: makeId(),
          reportType: detail.reportType,
          title: detail.title,
          fields: fromPayload(detail.reportType, detail.versions?.at(-1)?.payload || {}),
          basePayload: detail.versions?.at(-1)?.payload || null,
        });
        setReportType(detail.reportType);
        setTitle(detail.title);
        setFields(fromPayload(detail.reportType, detail.versions?.at(-1)?.payload || {}));
        setNotice("Editable report opened. Save creates a new version; submitted versions remain unchanged.");
      }
    } catch (err) { setError(err.message || "Could not open this report."); }
  }

  async function submitReport(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const local = saveLocalDraft();
      const payload = toPayload(reportType, fields);
      const request = { reportType, title: title.trim(), payload };
      if (!request.title) throw new Error("Enter a report title before submitting.");
      let detail;
      if (local.serverId) {
        detail = await reportSubmissionApi.get(local.serverId);
        const currentStatus = detail.lifecycleStatus || detail.status;
        if (["SUBMITTED", "PENDING_REVIEW", "FLAGGED", "BLOCKED", "APPROVED", "MERGED", "REJECTED"].includes(currentStatus)) {
          setNotice("This report has already left the draft stage. Its submitted version was not changed.");
          await refresh();
          setSelected(detail);
          return;
        }
        const latestPayload = detail.versions?.at(-1)?.payload || {};
        if (JSON.stringify(latestPayload) !== JSON.stringify(payload)
            || detail.title !== request.title || detail.reportType !== request.reportType) {
          detail = await reportSubmissionApi.save(local.serverId, request);
        }
      } else {
        detail = await reportSubmissionApi.create(request, local.idempotencyKey);
        const savedLocal = { ...local, serverId: detail.id, basePayload: payload, updatedAt: new Date().toISOString() };
        persistLocal([...localDrafts.filter(item => item.id !== savedLocal.id), savedLocal]);
      }
      const currentStatus = detail.lifecycleStatus || detail.status;
      if (currentStatus === "CHANGES_REQUESTED") {
        detail = await reportSubmissionApi.save(detail.id, request);
      }
      const submitted = await reportSubmissionApi.submit(detail.id);
      persistLocal(localDrafts.filter(item => item.id !== local.id));
      setSelected(submitted);
      resetForm();
      await refresh();
      setNotice("Report submitted. The submitted version is now locked for review.");
    } catch (err) {
      setError(err.message || "The report could not be submitted. Your local draft has been kept.");
    } finally { setSaving(false); }
  }

  async function postComment(event) {
    event.preventDefault();
    if (!selected?.id || !comment.trim()) return;
    setSaving(true);
    setError("");
    try {
      const updated = await reportSubmissionApi.comment(selected.id, comment.trim());
      setSelected(updated);
      setComment("");
      await refresh();
    } catch (err) { setError(err.message || "Comment could not be sent."); }
    finally { setSaving(false); }
  }

  function removeLocalDraft(id) {
    if (!window.confirm("Delete this local draft from this device? This does not delete any submitted report.")) return;
    persistLocal(localDrafts.filter(item => item.id !== id));
    if (editing?.id === id) resetForm();
  }

  return (
    <div className="my-reports-page">
      <section className="card full">
        <div className="section-head">
          <div><p className="eyebrow">CONTRIBUTIONS</p><h2>My reports</h2><p className="muted">Prepare a report, save it locally, then submit it explicitly while connected to the farm computer.</p></div>
          <button type="button" className="secondary-button" onClick={refresh} disabled={loading}>Refresh</button>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="form-success" role="status">{notice}</p>}
        <div className="report-submission-layout">
          <section className="report-composer">
            <div className="section-head"><h3>{editing ? "Edit report draft" : "New report"}</h3>{editing && <button type="button" className="text-button" onClick={resetForm}>New report</button>}</div>
            <form onSubmit={submitReport}>
              <div className="form-grid">
                <label>Report type
                  <select value={reportType} onChange={event => { setReportType(event.target.value); setFields(emptyFields(event.target.value)); setEditing(null); }}>
                    {Object.entries(reportDefinitions).map(([value, definition]) => <option key={value} value={value}>{definition.label}</option>)}
                  </select>
                </label>
                <label>Report title
                  <input value={title} onChange={event => setTitle(event.target.value)} maxLength={160} required placeholder="e.g. Morning mortality report" />
                </label>
                {selectedDefinition.fields.map(([key, label, kind, required]) => (
                  <label className={kind === "textarea" ? "field-span-2" : ""} key={key}>{label}
                    {kind === "textarea"
                      ? <textarea value={fields[key] || ""} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))} rows={3} required={required} />
                      : <input type={kind === "money" ? "number" : kind} step={kind === "decimal" || kind === "money" ? "0.01" : "1"} min={kind === "number" || kind === "decimal" || kind === "money" ? "0" : undefined} value={fields[key] || ""} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))} required={required} /> }
                  </label>
                ))}
              </div>
              <p className="muted report-draft-hint">Saving here stores a local draft only. It does not update official farm records or send anything to a Manager.</p>
              <div className="inline-form">
                <button type="button" className="secondary-button" onClick={saveLocalDraft}>Save draft</button>
                <button type="submit" disabled={saving}>{saving ? "Submitting…" : "Submit for review"}</button>
              </div>
            </form>
          </section>
          <section className="report-list">
            <div className="section-head"><h3>Submission history</h3><span className="muted">{allReports.length} reports</span></div>
            {loading ? <p className="muted">Loading your reports…</p> : allReports.length === 0
              ? <p className="muted">No reports yet. Local drafts and submitted reports will appear here.</p>
              : <div className="report-submission-list">
                {allReports.map(report => (
                  <article className="report-submission-item" key={report.id}>
                    <div className="report-submission-item-heading">
                      <strong>{report.title || "Untitled draft"}</strong>
                      <span className={report.localOnly ? "status-pill status-local" : "status-pill"}>{report.localOnly ? "Local draft" : statusLabel(report)}</span>
                    </div>
                    <p className="muted">{reportDefinitions[report.reportType]?.label || report.reportType} · {report.updatedAt || report.createdAt || "Not submitted"}</p>
                    <div className="inline-form">
                      {report.localOnly
                        ? <button type="button" className="secondary-button" onClick={() => openLocalDraft(report)}>Open</button>
                        : <button type="button" className="secondary-button" onClick={() => openServerReport(report)}>View</button>}
                      {report.localOnly && <button type="button" className="text-button" onClick={() => removeLocalDraft(report.id)}>Discard</button>}
                    </div>
                  </article>
                ))}
              </div>}
          </section>
        </div>
        {selected && <section className="report-detail">
          <div className="section-head"><div><p className="eyebrow">REPORT DETAIL</p><h3>{selected.title}</h3><p className="muted">Reference {selected.id} · {statusLabel(selected)} · version {selected.submittedVersion || selected.currentVersion}</p></div><button type="button" className="text-button" onClick={() => setSelected(null)}>Close</button></div>
          <div className="report-history">
            <h4>Version history</h4>
            {(selected.versions || []).map(version => <div key={version.version}><strong>Version {version.version}</strong><span>{version.title}</span><small>{version.createdAt}</small></div>)}
            <h4>Activity</h4>
            {(selected.history || []).map(event => <div key={event.id}><strong>{event.event.replaceAll("_", " ")}</strong><span>{event.occurredAt}</span></div>)}
          </div>
          {(selected.comments || []).length > 0 && <div className="report-comments"><h4>Discussion</h4>{selected.comments.map(item => <article key={item.id}><strong>{item.authorName || "Farm user"}</strong><p>{item.body}</p><small>{item.createdAt}</small></article>)}</div>}
          {["SUBMITTED", "PENDING_REVIEW", "CHANGES_REQUESTED"].includes(selected.lifecycleStatus || selected.status) && <form className="report-comment-form" onSubmit={postComment}>
            <label htmlFor="report-comment">Add a comment</label>
            <textarea id="report-comment" rows={2} value={comment} onChange={event => setComment(event.target.value)} maxLength={4000} />
            <button type="submit" disabled={saving || !comment.trim()}>Send comment</button>
          </form>}
        </section>}
      </section>
    </div>
  );
}
