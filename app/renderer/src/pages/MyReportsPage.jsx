import { useCallback, useEffect, useMemo, useState } from "react";
import { reportSubmissionApi } from "../services/api.js";
import { parseNairaToKobo } from "../utils/moneyInput.js";

const reportDefinitions = {
  DAILY_OPERATIONS: {
    label: "Daily operations",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Observation date", "date", true],
      ["batchId", "Batch / flock", "batch", true],
      ["notes", "What happened today?", "textarea", true],
      ["waterNotes", "Water observations", "textarea", false],
    ],
  },
  MORTALITY: {
    label: "Mortality",
    dateKey: "eventDate",
    fields: [
      ["eventDate", "Date observed", "date", true],
      ["batchId", "Batch / flock", "batch", true],
      ["quantity", "Number of birds", "number", true],
      ["reason", "Suspected cause / reason", "text", false],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  CULLING: {
    label: "Culling",
    dateKey: "eventDate",
    fields: [
      ["eventDate", "Event date", "date", true],
      ["batchId", "Batch / flock", "batch", true],
      ["quantity", "Number of birds", "number", true],
      ["reason", "Reason for culling", "text", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  HEALTH_RECORD: {
    label: "Health observation",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Observation date", "date", true],
      ["batchId", "Batch / flock", "batch", true],
      ["conditionProblem", "Condition / problem", "text", true],
      ["description", "What did you observe?", "textarea", true],
      ["action", "Action taken", "textarea", true],
    ],
  },
  DRUG_TREATMENT: {
    label: "Drug / treatment",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Treatment date", "date", true],
      ["batchId", "Batch / flock", "batch", true],
      ["drug", "Drug / product", "text", true],
      ["quantity", "Quantity used", "number", true],
      ["costNaira", "Cost (₦)", "money", true],
      ["reason", "Reason / dosage notes", "textarea", true],
    ],
  },
  VACCINATION: {
    label: "Vaccination",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Vaccination date", "date", true],
      ["batchId", "Batch / flock", "batch", true],
      ["vaccine", "Vaccine", "text", true],
      ["dose", "Dose", "text", true],
      ["quantity", "Birds vaccinated", "number", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  EGG_COLLECTION: {
    label: "Egg collection",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Collection date", "date", true],
      ["batchId", "Layer batch", "batch", true],
      ["good", "Good eggs", "number", true],
      ["cracked", "Cracked eggs", "number", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  WEIGHT_RECORD: {
    label: "Weight record",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Measurement date", "date", true],
      ["batchId", "Broiler / turkey batch", "batch", true],
      ["sampleQuantity", "Birds sampled", "number", true],
      ["totalWeightKg", "Total sample weight (kg)", "decimal", true],
      ["notes", "Additional notes", "textarea", false],
    ],
  },
  FEED_USAGE: {
    label: "Feed usage",
    dateKey: "usageDate",
    fields: [
      ["eventDate", "Usage date", "date", true],
      ["batchId", "Batch / flock", "batch", true],
      ["feedTypeId", "Feed type", "feed", true],
      ["quantity", "Quantity used", "decimal", true],
      ["reason", "Notes", "textarea", false],
    ],
  },
  EXPENSE: {
    label: "Farm expense",
    dateKey: "occurredDate",
    fields: [
      ["eventDate", "Expense date", "date", true],
      ["batchId", "Batch (optional)", "batchOptional", false],
      ["description", "Description", "text", true],
      ["amountNaira", "Amount (₦)", "money", true],
      ["category", "Category", "category", true],
    ],
  },
  BIRD_SALE: {
    label: "Bird sale",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Sale date", "date", true],
      ["batchId", "Broiler / turkey batch", "batch", true],
      ["quantity", "Birds sold", "number", true],
      ["pricePerBirdNaira", "Price per bird (₦)", "money", true],
      ["customer", "Customer", "text", true],
    ],
  },
  EGG_SALE: {
    label: "Egg sale",
    dateKey: "recordDate",
    fields: [
      ["eventDate", "Sale date", "date", true],
      ["batchId", "Layer batch", "batch", true],
      ["crates", "Crates sold", "decimal", true],
      ["pricePerCrateNaira", "Price per crate (₦)", "money", true],
      ["customer", "Customer", "text", true],
    ],
  },
};

const editableStatuses = ["DRAFT", "CHANGES_REQUESTED", "BLOCKED"];

function makeId() {
  return globalThis.crypto?.randomUUID?.() || "report-" + Date.now() + "-" + Math.random().toString(36).slice(2);
}

function today() {
  const date = new Date();
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

function currentTime() {
  const date = new Date();
  return String(date.getHours()).padStart(2, "0") + ":" + String(date.getMinutes()).padStart(2, "0");
}

function readLocalArray(key) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function emptyFields(type) {
  const values = {};
  for (const [key, , kind] of reportDefinitions[type].fields) {
    values[key] = kind === "date" ? today() : kind === "category" ? "OTHER" : "";
  }
  values.eventTime = currentTime();
  values.lateReason = "";
  return values;
}

function moneyPayloadKey(key) {
  if (key === "costNaira") return "costMinor";
  if (key === "pricePerBirdNaira") return "pricePerBirdMinor";
  if (key === "pricePerCrateNaira") return "pricePerCrateMinor";
  return "amountMinor";
}

function toPayload(type, values, references) {
  const definition = reportDefinitions[type];
  const payload = {};
  for (const [key, , kind] of definition.fields) {
    const value = values[key] ?? "";
    if (value === "") continue;
    const target = key === "eventDate" ? definition.dateKey : key;
    if (kind === "number") payload[target] = Number.parseInt(value, 10);
    else if (kind === "decimal") payload[target] = Number(value);
    else if (kind === "money") payload[moneyPayloadKey(key)] = parseNairaToKobo(value);
    else if (kind === "batch" || kind === "batchOptional") payload.batchId = value;
    else payload[target] = value;
  }
  if (values.eventDate) payload.eventDate = values.eventDate;
  if (values.eventTime) payload.eventTime = values.eventTime;
  if (values.lateReason?.trim()) payload.lateReason = values.lateReason.trim();
  if (type === "FEED_USAGE") {
    const feed = references.feedTypes.find(item => item.id === values.feedTypeId);
    if (feed) payload.unit = feed.unit;
  }
  return payload;
}

function fromPayload(type, payload = {}) {
  const definition = reportDefinitions[type];
  if (!definition) return {};
  const values = emptyFields(type);
  for (const [key] of definition.fields) {
    let value = key === "eventDate" ? payload[definition.dateKey] : payload[key];
    if (key === "costNaira" || key === "pricePerBirdNaira" || key === "pricePerCrateNaira" || key === "amountNaira") {
      const minor = payload[moneyPayloadKey(key)];
      value = minor == null ? "" : (Number(minor) / 100).toFixed(2);
    }
    if (value != null) values[key] = String(value);
  }
  values.eventTime = payload.eventTime || currentTime();
  values.lateReason = payload.lateReason || "";
  return values;
}

function statusLabel(report) {
  return String(report.lifecycleStatus || report.status || "DRAFT").replaceAll("_", " ").toLowerCase()
    .replace(/\b\w/g, letter => letter.toUpperCase());
}

function reportPayload(report) {
  return report?.versions?.at(-1)?.payload || {};
}

function samePayload(left, right) {
  return JSON.stringify(left || {}) === JSON.stringify(right || {});
}

export default function MyReportsPage({ userId = "" }) {
  const storageKey = "pomas:report-submission-drafts:" + (userId || "local-user");
  const referencesStorageKey = "pomas:report-submission-references:" + (userId || "local-user");
  const [serverReports, setServerReports] = useState([]);
  const [references, setReferences] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(referencesStorageKey) || '{"batches":[],"feedTypes":[]}');
    } catch {
      return { batches: [], feedTypes: [] };
    }
  });
  const [localDrafts, setLocalDrafts] = useState(() => readLocalArray(storageKey));
  const [reportType, setReportType] = useState("MORTALITY");
  const [title, setTitle] = useState("");
  const [fields, setFields] = useState(() => emptyFields("MORTALITY"));
  const [editing, setEditing] = useState(null);
  const [selected, setSelected] = useState(null);
  const [comment, setComment] = useState("");
  const [loading, setLoading] = useState(true);
  const [backendAvailable, setBackendAvailable] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedDefinition = reportDefinitions[reportType];
  const allReports = useMemo(() => {
    const localOnly = localDrafts.filter(item => !item.serverId)
      .map(item => ({ ...item, localOnly: true, status: "LOCAL_DRAFT" }));
    const serverItems = serverReports.map(report => ({
      ...report,
      localCopy: localDrafts.find(item => item.serverId === report.id) || null,
    }));
    return [...localOnly, ...serverItems]
      .sort((a, b) => String(b.updatedAt || b.createdAt || "").localeCompare(String(a.updatedAt || a.createdAt || "")));
  }, [localDrafts, serverReports]);

  function persistLocal(next) {
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
      setLocalDrafts(next);
      return true;
    } catch {
      setError("Pomas could not store this draft on the device. Free up local storage before continuing.");
      return false;
    }
  }

  function persistReferences(next) {
    setReferences(next);
    try { localStorage.setItem(referencesStorageKey, JSON.stringify(next)); } catch { /* cached references are optional */ }
  }

  const refresh = useCallback(async () => {
    setLoading(true);
    setError("");
    const [reportsResult, referencesResult] = await Promise.allSettled([
      reportSubmissionApi.mine(), reportSubmissionApi.references(),
    ]);
    if (reportsResult.status === "fulfilled") {
      setBackendAvailable(true);
      setServerReports(reportsResult.value);
    } else {
      setBackendAvailable(false);
      setError(reportsResult.reason?.message || "Could not load your server submissions.");
    }
    if (referencesResult.status === "fulfilled") persistReferences(referencesResult.value);
    else {
      setNotice("Farm lists could not be refreshed. Cached batch and feed references remain available.");
    }
    setLoading(false);
  }, [userId]);

  useEffect(() => { refresh(); }, [refresh]);

  function resetForm() {
    setEditing(null);
    setReportType("MORTALITY");
    setTitle("");
    setFields(emptyFields("MORTALITY"));
    setNotice("");
  }

  function buildDraft() {
    return {
      id: editing?.id || makeId(),
      serverId: editing?.serverId || null,
      idempotencyKey: editing?.idempotencyKey || makeId(),
      reportType,
      title: title.trim(),
      fields: { ...fields },
      updatedAt: new Date().toISOString(),
      basePayload: editing?.basePayload || null,
      baseVersion: editing?.baseVersion || null,
    };
  }

  function saveLocalCopy() {
    const draft = buildDraft();
    const next = [...localDrafts.filter(item => item.id !== draft.id), draft];
    if (!persistLocal(next)) return null;
    setEditing(draft);
    return draft;
  }

  function openLocalDraft(draft) {
    setEditing(draft);
    setReportType(draft.reportType);
    setTitle(draft.title || "");
    setFields({ ...emptyFields(draft.reportType), ...draft.fields });
    setSelected(null);
    setError("");
    setNotice("Local draft reopened. It remains on this device until you explicitly sync or submit it.");
  }

  async function syncDraft(draft) {
    const payload = toPayload(draft.reportType, draft.fields, references);
    const request = { reportType: draft.reportType, title: draft.title, payload };
    if (!request.title) throw new Error("Enter a report title before saving to the farm computer.");
    let detail;
    if (draft.serverId) {
      detail = await reportSubmissionApi.get(draft.serverId);
      const status = detail.lifecycleStatus || detail.status;
      if (!editableStatuses.includes(status)) {
        throw new Error("This report is no longer an editable draft. Your local copy has been kept.");
      }
      if (!samePayload(reportPayload(detail), payload)
          || detail.title !== request.title || detail.reportType !== request.reportType) {
        detail = await reportSubmissionApi.save(draft.serverId, request);
      }
    } else {
      detail = await reportSubmissionApi.create(request, draft.idempotencyKey);
    }
    const synced = {
      ...draft,
      serverId: detail.id,
      basePayload: payload,
      baseVersion: detail.currentVersion,
      updatedAt: new Date().toISOString(),
    };
    const next = [...localDrafts.filter(item => item.id !== synced.id), synced];
    if (!persistLocal(next)) throw new Error("The server draft was saved, but its local recovery copy could not be updated. Do not submit until local storage is available.");
    setEditing(synced);
    return detail;
  }

  async function saveDraft() {
    setSaving(true);
    setError("");
    setNotice("");
    const local = saveLocalCopy();
    if (!local) {
      setSaving(false);
      return;
    }
    if (backendAvailable !== true) {
      setNotice("Draft saved on this device. Server sync will be available when the farm computer reconnects.");
      setSaving(false);
      return;
    }
    try {
      const detail = await syncDraft(local);
      setSelected(detail);
      await refresh();
      setNotice("Draft saved on this device and to the farm computer. It has not been submitted.");
    } catch (err) {
      setNotice("Draft saved on this device. Server sync is pending; reconnect and use Save draft or Submit for review.");
      setError(err.message || "The local draft could not be synced.");
    } finally {
      setSaving(false);
    }
  }

  async function openServerReport(report) {
    setError("");
    try {
      const detail = await reportSubmissionApi.get(report.id);
      setSelected(detail);
      const status = detail.lifecycleStatus || detail.status;
      if (editableStatuses.includes(status)) {
        const shadow = localDrafts.find(item => item.serverId === detail.id);
        const latestPayload = reportPayload(detail);
        const draft = shadow || {
          id: "server-" + detail.id,
          serverId: detail.id,
          idempotencyKey: makeId(),
          reportType: detail.reportType,
          title: detail.title,
          fields: fromPayload(detail.reportType, latestPayload),
          basePayload: latestPayload,
          baseVersion: detail.currentVersion,
        };
        setEditing(draft);
        setReportType(draft.reportType);
        setTitle(draft.title);
        setFields({ ...emptyFields(draft.reportType), ...draft.fields });
        setNotice("Draft opened. Save draft records a new version on the farm computer; submitted versions stay unchanged.");
      } else {
        setEditing(null);
      }
    } catch (err) {
      setError(err.message || "Could not open this report.");
    }
  }

  async function submitReport(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    const local = saveLocalCopy();
    if (!local) {
      setSaving(false);
      return;
    }
    try {
      const detail = await syncDraft(local);
      const status = detail.lifecycleStatus || detail.status;
      if (!editableStatuses.includes(status)) {
        setSelected(detail);
        throw new Error("This report has already been submitted or moved out of draft. Its saved version was not changed.");
      }
      const submitted = await reportSubmissionApi.submit(detail.id);
      persistLocal(localDrafts.filter(item => item.id !== local.id));
      setSelected(submitted);
      resetForm();
      await refresh();
      setNotice("Report submitted. The submitted version is locked for review. Retrying will not create another report.");
    } catch (err) {
      setError(err.message || "The report could not be submitted. Your local recovery copy has been kept.");
      setNotice("Your latest draft is saved on this device. It has not been silently submitted.");
    } finally {
      setSaving(false);
    }
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
    } catch (err) {
      setError(err.message || "Comment could not be sent.");
    } finally {
      setSaving(false);
    }
  }

  function removeLocalDraft(id) {
    if (!window.confirm("Discard this local recovery copy from this device? A server-side draft or submitted report is not deleted.")) return;
    persistLocal(localDrafts.filter(item => item.id !== id));
    if (editing?.id === id) resetForm();
  }

  function renderField([key, label, kind, required]) {
    if (kind === "batch" || kind === "batchOptional") {
      const allowedTypes = reportType === "EGG_COLLECTION" || reportType === "EGG_SALE"
        ? ["LAYER"] : reportType === "WEIGHT_RECORD" || reportType === "BIRD_SALE"
          ? ["BROILER", "TURKEY"] : null;
      const options = references.batches.filter(batch => !allowedTypes || allowedTypes.includes(batch.type));
      return <label key={key}>{label}
        <select value={fields[key] || ""} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))} required={required}>
          <option value="">{kind === "batchOptional" ? "Farm-wide / no batch" : "Select an active batch"}</option>
          {options.map(batch => <option key={batch.id} value={batch.id}>{batch.code} · {batch.type}</option>)}
        </select>
      </label>;
    }
    if (kind === "feed") {
      const selectedBatch = references.batches.find(item => item.id === fields.batchId);
      const options = references.feedTypes.filter(feed => !selectedBatch || feed.applicableTypes?.includes(selectedBatch.type));
      return <label key={key}>{label}
        <select value={fields[key] || ""} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))} required={required}>
          <option value="">Select an active feed type</option>
          {options.map(feed => <option key={feed.id} value={feed.id}>{feed.name} · {feed.unit}</option>)}
        </select>
      </label>;
    }
    if (kind === "category") {
      return <label key={key}>{label}
        <select value={fields[key] || "OTHER"} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))} required={required}>
          <option value="FEED">Feed</option><option value="DRUGS">Drugs</option><option value="OTHER">Other</option>
        </select>
      </label>;
    }
    return <label className={kind === "textarea" ? "field-span-2" : ""} key={key}>{label}
      {kind === "textarea"
        ? <textarea value={fields[key] || ""} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))} rows={3} required={required} />
        : <input type={kind === "money" ? "number" : kind} step={kind === "decimal" || kind === "money" ? "0.01" : "1"} min={kind === "number" || kind === "decimal" || kind === "money" ? "0" : undefined} value={fields[key] || ""} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))} required={required} />}
    </label>;
  }

  return (
    <div className="my-reports-page">
      <section className="card full">
        <div className="section-head">
          <div><p className="eyebrow">WORKER WORKSPACE</p><h2>My submissions</h2><p className="muted">Create a report, keep a recoverable local draft, then submit it explicitly when the farm computer is available.</p></div>
          <button type="button" className="secondary-button" onClick={refresh} disabled={loading || saving}>Refresh</button>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="form-success" role="status">{notice}</p>}
        <div className="report-submission-layout">
          <section className="report-composer">
            <div className="section-head"><h3>{editing ? "Edit report draft" : "New report"}</h3>{editing && <button type="button" className="text-button" onClick={resetForm}>New report</button>}</div>
            <form onSubmit={submitReport}>
              <div className="form-grid">
                <label>Report type
                  <select value={reportType} onChange={event => { setReportType(event.target.value); setFields(emptyFields(event.target.value)); setEditing(null); setSelected(null); }}>
                    {Object.entries(reportDefinitions).map(([value, definition]) => <option key={value} value={value}>{definition.label}</option>)}
                  </select>
                </label>
                <label>Report title
                  <input value={title} onChange={event => setTitle(event.target.value)} maxLength={160} required placeholder="e.g. Morning mortality report" />
                </label>
                {selectedDefinition.fields.map(renderField)}
                <label>Time of event
                  <input type="time" value={fields.eventTime || ""} onChange={event => setFields(current => ({ ...current, eventTime: event.target.value }))} required />
                </label>
                <label className="field-span-2">Reason for late report (if more than 24 hours)
                  <textarea rows={2} value={fields.lateReason || ""} onChange={event => setFields(current => ({ ...current, lateReason: event.target.value }))} maxLength={1000} />
                </label>
              </div>
              <p className="muted report-draft-hint">Save draft stores a recovery copy on this device and syncs a server draft when connected. It never updates official farm ledgers or submits the report.</p>
              <div className="inline-form">
                <button type="button" className="secondary-button" onClick={saveDraft} disabled={saving}>{saving ? "Saving…" : "Save draft"}</button>
                <button type="submit" disabled={saving}>{saving ? "Working…" : "Submit for review"}</button>
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
                    <p className="muted">{reportDefinitions[report.reportType]?.label || report.reportType} · {report.updatedAt || report.createdAt || "Not saved"}</p>
                    {report.localCopy && <p className="muted">A local recovery copy is available on this device.</p>}
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
          <div className="section-head"><div><p className="eyebrow">REPORT DETAIL</p><h3>{selected.title}</h3><p className="muted">Reference {selected.id} · {statusLabel(selected)} · submitted version {selected.submittedVersion || "Not submitted"} · current version {selected.currentVersion}</p></div><button type="button" className="text-button" onClick={() => setSelected(null)}>Close</button></div>
          <div className="report-history">
            {(selected.links || []).length > 0 && <><h4>Official farm records</h4>{selected.links.map(link => <div key={link.id}><strong>{link.recordType.replaceAll("_", " ")}</strong><span>{link.recordId}</span></div>)}</>}
            {(selected.checks || []).length > 0 && <section className="report-checks">
              <h4>Submission checks</h4>
              {selected.checks.map(check => <article key={check.id}>
                <span className={`status-pill status-${String(check.outcome || "").toLowerCase()}`}>{String(check.outcome || "").replaceAll("_", " ")}</span>
                <strong>{String(check.code || "").replaceAll("_", " ")}</strong>
                <p>{check.explanation}</p>
              </article>)}
            </section>}
            <h4>Version history</h4>
            {(selected.versions || []).map(version => (
              <details key={version.version}>
                <summary><strong>Version {version.version}</strong> · {version.title} <small>{version.createdAt}</small></summary>
                <pre>{JSON.stringify(version.payload || {}, null, 2)}</pre>
              </details>
            ))}
            <h4>Activity history</h4>
            {(selected.history || []).map(item => <div key={item.id}><strong>{String(item.event || "").replaceAll("_", " ")}</strong><span>{item.occurredAt}</span></div>)}
          </div>
          {(selected.comments || []).length > 0 && <div className="report-comments"><h4>Discussion</h4>{selected.comments.map(item => <article key={item.id}><strong>{item.authorName || "Farm user"}</strong><p>{item.body}</p><small>{item.createdAt}</small></article>)}</div>}
          {["SUBMITTED", "PENDING_REVIEW", "CHANGES_REQUESTED", "BLOCKED", "FLAGGED"].includes(selected.lifecycleStatus || selected.status) && <form className="report-comment-form" onSubmit={postComment}>
            <label htmlFor="report-comment">Add a comment</label>
            <textarea id="report-comment" rows={2} value={comment} onChange={event => setComment(event.target.value)} maxLength={4000} />
            <button type="submit" disabled={saving || !comment.trim()}>Send comment</button>
          </form>}
        </section>}
      </section>
    </div>
  );
}
