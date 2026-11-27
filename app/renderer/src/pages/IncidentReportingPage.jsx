import { useCallback, useEffect, useState } from "react";
import { incidentApi, reportReviewApi, reportSubmissionApi, reportingConfigApi } from "../services/api.js";

const incidentTypes = [
  ["SUSPECTED_DISEASE", "Suspected disease"], ["UNUSUAL_MORTALITY", "Unusual mortality"],
  ["WATER_FAILURE", "Water failure"], ["FEED_FAILURE", "Feed failure"],
  ["FIRE", "Fire"], ["FLOOD", "Flood"], ["SECURITY", "Security incident"],
  ["EQUIPMENT_DAMAGE", "Equipment damage"], ["OTHER", "Other"],
];
const severities = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
const reportTypes = [
  "DAILY_OPERATIONS", "MORTALITY", "CULLING", "HEALTH_RECORD", "DRUG_TREATMENT",
  "VACCINATION", "EGG_COLLECTION", "WEIGHT_RECORD", "FEED_USAGE", "EXPENSE", "BIRD_SALE", "EGG_SALE",
];
const label = value => String(value || "").replaceAll("_", " ").toLowerCase().replace(/\\b\\w/g, char => char.toUpperCase());
const localDateTime = () => {
  const date = new Date();
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
};

const emptyIncident = () => ({
  type: "OTHER", severity: "MEDIUM", title: "", description: "",
  batchId: "", houseId: "", eventAt: localDateTime(),
});
const emptySchedule = () => ({
  reportType: "", scopeType: "FARM", scopeId: "", batchType: "",
  frequency: "DAILY", dueTime: "", dayOfWeek: "", graceMinutes: "0",
});

export default function IncidentReportingPage({ currentUser }) {
  const manager = currentUser?.role === "MANAGER";
  const [incidents, setIncidents] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [schedules, setSchedules] = useState([]);
  const [missing, setMissing] = useState([]);
  const [references, setReferences] = useState({ batches: [] });
  const [incidentForm, setIncidentForm] = useState(emptyIncident);
  const [scheduleForm, setScheduleForm] = useState(emptySchedule);
  const [resolutionNotes, setResolutionNotes] = useState({});
  const [activeTab, setActiveTab] = useState("incidents");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [incidentRows, notificationRows, referenceRows] = await Promise.all([
        incidentApi.list(), reportReviewApi.notifications(), reportSubmissionApi.references(),
      ]);
      setIncidents(incidentRows);
      setNotifications(notificationRows);
      setReferences(referenceRows || { batches: [] });
      if (manager) {
        const [scheduleRows, missingRows] = await Promise.all([
          reportingConfigApi.schedules(), reportingConfigApi.missing(),
        ]);
        setSchedules(scheduleRows);
        setMissing(missingRows);
      }
    } catch (err) {
      setError(err.message || "Emergency and reporting information could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [manager]);

  useEffect(() => { refresh(); }, [refresh]);

  async function submitIncident(event) {
    event.preventDefault();
    setSaving(true); setError(""); setNotice("");
    try {
      await incidentApi.create({
        ...incidentForm,
        batchId: incidentForm.batchId || null,
        houseId: incidentForm.houseId || null,
      });
      setIncidentForm(emptyIncident());
      setNotice("Emergency alert sent. Managers have been notified. This alert does not change official farm records.");
      await refresh();
    } catch (err) { setError(err.message || "Emergency alert could not be submitted."); }
    finally { setSaving(false); }
  }

  async function acknowledge(id) {
    setSaving(true); setError("");
    try {
      await incidentApi.acknowledge(id);
      setNotice("Incident acknowledged and recorded in its response history.");
      await refresh();
    } catch (err) { setError(err.message || "Incident could not be acknowledged."); }
    finally { setSaving(false); }
  }

  async function resolve(id) {
    const notes = (resolutionNotes[id] || "").trim();
    if (!notes) { setError("Add resolution notes before closing an incident."); return; }
    setSaving(true); setError("");
    try {
      await incidentApi.resolve(id, notes);
      setResolutionNotes(current => ({ ...current, [id]: "" }));
      setNotice("Incident marked resolved. The response notes are retained.");
      await refresh();
    } catch (err) { setError(err.message || "Incident could not be resolved."); }
    finally { setSaving(false); }
  }

  async function escalate(id) {
    const notes = window.prompt("Why does this incident need renewed Manager attention?");
    if (!notes?.trim()) return;
    setSaving(true); setError("");
    try {
      await incidentApi.escalate(id, notes.trim());
      setNotice("Escalation sent to Managers and added to the incident history.");
      await refresh();
    } catch (err) { setError(err.message || "Incident could not be escalated."); }
    finally { setSaving(false); }
  }

  async function createSchedule(event) {
    event.preventDefault();
    setSaving(true); setError(""); setNotice("");
    try {
      await reportingConfigApi.createSchedule({
        ...scheduleForm,
        scopeId: scheduleForm.scopeType === "FARM" ? null : scheduleForm.scopeId.trim(),
        batchType: scheduleForm.batchType || null,
        dayOfWeek: scheduleForm.frequency === "WEEKLY" ? Number(scheduleForm.dayOfWeek) : null,
        graceMinutes: Number(scheduleForm.graceMinutes),
      });
      setScheduleForm(emptySchedule());
      setNotice("Reporting schedule saved. Missing-report tracking will begin after its configured deadline and grace period.");
      await refresh();
    } catch (err) { setError(err.message || "Reporting schedule could not be saved."); }
    finally { setSaving(false); }
  }

  async function toggleSchedule(schedule) {
    setSaving(true); setError("");
    try {
      await reportingConfigApi.setScheduleEnabled(schedule.id, !schedule.enabled);
      setNotice(schedule.enabled ? "Schedule paused." : "Schedule enabled.");
      await refresh();
    } catch (err) { setError(err.message || "Schedule could not be updated."); }
    finally { setSaving(false); }
  }

  async function markRead(notification) {
    try {
      await reportReviewApi.markNotificationRead(notification.id);
      setNotifications(rows => rows.map(row => row.id === notification.id ? { ...row, readAt: new Date().toISOString() } : row));
    } catch (err) { setError(err.message || "Notification could not be marked as read."); }
  }

  function openNotification(notification) {
    if (notification.entityType === "FARM_INCIDENT") {
      setActiveTab("incidents");
      window.location.hash = "#incidents/overview";
    } else if (notification.entityType === "REPORT_SUBMISSION") {
      window.location.hash = "#review-centre/overview";
    }
    if (!notification.readAt) markRead(notification);
  }

  const unread = notifications.filter(item => !item.readAt).length;

  return (
    <div className="incident-reporting-page">
      <section className="card full">
        <div className="section-head">
          <div><p className="eyebrow">FARM SAFETY & ACCOUNTABILITY</p><h2>Emergency alerts & reporting</h2>
            <p className="muted">Report urgent incidents immediately. Alerts are separate from official farm records.</p></div>
          <button type="button" className="secondary-button" onClick={refresh} disabled={loading}>Refresh</button>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="form-success" role="status">{notice}</p>}
        <nav className="page-tabs" aria-label="Emergency and reporting sections">
          <button type="button" className={activeTab === "incidents" ? "page-tab active" : "page-tab"} onClick={() => setActiveTab("incidents")}>Emergency incidents</button>
          {manager && <button type="button" className={activeTab === "schedules" ? "page-tab active" : "page-tab"} onClick={() => setActiveTab("schedules")}>Schedules & missing reports <span>{missing.length}</span></button>}
          <button type="button" className={activeTab === "notifications" ? "page-tab active" : "page-tab"} onClick={() => setActiveTab("notifications")}>Notifications {unread > 0 && <span>{unread}</span>}</button>
        </nav>

        {activeTab === "incidents" && <>
          <section className="user-create-section">
            <h3>Submit emergency alert</h3>
            <p className="muted">Use this for urgent risks such as suspected disease, unusual mortality, water/feed failure, fire, flooding, or security incidents. Do not wait for the normal report review process.</p>
            <form className="form-grid" onSubmit={submitIncident}>
              <label>Incident category<select value={incidentForm.type} onChange={event => setIncidentForm(current => ({ ...current, type: event.target.value }))}>{incidentTypes.map(([value, text]) => <option key={value} value={value}>{text}</option>)}</select></label>
              <label>Severity<select value={incidentForm.severity} onChange={event => setIncidentForm(current => ({ ...current, severity: event.target.value }))}>{severities.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></label>
              <label>Title<input value={incidentForm.title} onChange={event => setIncidentForm(current => ({ ...current, title: event.target.value }))} required maxLength={160} /></label>
              <label>Observed at<input type="datetime-local" value={incidentForm.eventAt} onChange={event => setIncidentForm(current => ({ ...current, eventAt: event.target.value }))} required /></label>
              <label>Related batch (optional)<select value={incidentForm.batchId} onChange={event => setIncidentForm(current => ({ ...current, batchId: event.target.value }))}><option value="">Not batch-specific</option>{(references.batches || []).map(batch => <option key={batch.id} value={batch.id}>{batch.code} · {batch.type}</option>)}</select></label>
              <label>House ID (optional)<input value={incidentForm.houseId} onChange={event => setIncidentForm(current => ({ ...current, houseId: event.target.value }))} placeholder="House record ID" /></label>
              <label className="full-span">What happened?<textarea value={incidentForm.description} onChange={event => setIncidentForm(current => ({ ...current, description: event.target.value }))} required minLength={1} maxLength={4000} rows={4} /></label>
              <div className="form-actions"><button type="submit" disabled={saving}>{saving ? "Sending…" : "Send emergency alert"}</button></div>
            </form>
          </section>
          <section className="user-list-section">
            <h3>{manager ? "Farm incident queue" : "My emergency alerts"}</h3>
            {loading ? <p className="muted">Loading incidents…</p> : incidents.length === 0 ? <p className="muted">No emergency incidents have been reported.</p> : <div className="table-wrap"><table className="data-table">
              <thead><tr><th>Incident</th><th>Reported by</th><th>Severity</th><th>Status</th><th>Observed</th>{manager && <th>Response</th>}</tr></thead>
              <tbody>{incidents.map(item => <tr key={item.id}>
                <td><strong>{item.title}</strong><small>{label(item.incidentType)} · {item.description}</small></td>
                <td>{item.reporterName || item.reporterUsername || "You"}</td>
                <td><span className={"status-pill " + (item.severity === "CRITICAL" || item.severity === "HIGH" ? "status-suspended" : "")}>{item.severity}</span></td>
                <td>{label(item.status)}</td>
                <td>{item.eventAt}</td>
                {manager && <td>{item.status !== "RESOLVED" && <div className="stacked-actions">
                  {item.status === "OPEN" && <button type="button" className="secondary-button" disabled={saving} onClick={() => acknowledge(item.id)}>Acknowledge</button>}
                  <button type="button" className="secondary-button" disabled={saving} onClick={() => escalate(item.id)}>Escalate</button>
                  <div className="inline-form"><input aria-label={"Resolution notes for " + item.title} value={resolutionNotes[item.id] || ""} onChange={event => setResolutionNotes(current => ({ ...current, [item.id]: event.target.value }))} placeholder="Resolution notes" maxLength={2000} /><button type="button" disabled={saving} onClick={() => resolve(item.id)}>Resolve</button></div>
                </div>}</td>}
              </tr>)}</tbody>
            </table></div>}
          </section>
        </>}

        {manager && activeTab === "schedules" && <>
          <section className="user-create-section">
            <h3>Configure a reporting schedule</h3>
            <p className="muted">No schedule is assumed. Add only the reports and deadlines your farm actually requires. Times use the farm timezone.</p>
            <form className="form-grid" onSubmit={createSchedule}>
              <label>Report type<select value={scheduleForm.reportType} onChange={event => setScheduleForm(current => ({ ...current, reportType: event.target.value }))} required><option value="">Choose report type</option>{reportTypes.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></label>
              <label>Scope<select value={scheduleForm.scopeType} onChange={event => setScheduleForm(current => ({ ...current, scopeType: event.target.value, scopeId: "" }))}><option value="FARM">Whole farm</option><option value="HOUSE">House / pen</option><option value="BATCH">Batch / flock</option></select></label>
              {scheduleForm.scopeType !== "FARM" && <label>Scope record ID<input value={scheduleForm.scopeId} onChange={event => setScheduleForm(current => ({ ...current, scopeId: event.target.value }))} required placeholder="Paste the house or batch ID" /></label>}
              <label>Applies to batch type (optional)<select value={scheduleForm.batchType} onChange={event => setScheduleForm(current => ({ ...current, batchType: event.target.value }))}><option value="">All types</option><option value="LAYER">Layer</option><option value="BROILER">Broiler</option><option value="TURKEY">Turkey</option></select></label>
              <label>Frequency<select value={scheduleForm.frequency} onChange={event => setScheduleForm(current => ({ ...current, frequency: event.target.value, dayOfWeek: event.target.value === "WEEKLY" ? "1" : "" }))}><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option></select></label>
              {scheduleForm.frequency === "WEEKLY" && <label>Day of week<select value={scheduleForm.dayOfWeek} onChange={event => setScheduleForm(current => ({ ...current, dayOfWeek: event.target.value }))}><option value="1">Monday</option><option value="2">Tuesday</option><option value="3">Wednesday</option><option value="4">Thursday</option><option value="5">Friday</option><option value="6">Saturday</option><option value="7">Sunday</option></select></label>}
              <label>Due time (farm local)<input type="time" value={scheduleForm.dueTime} onChange={event => setScheduleForm(current => ({ ...current, dueTime: event.target.value }))} required /></label>
              <label>Grace period (minutes)<input type="number" min="0" max="1440" value={scheduleForm.graceMinutes} onChange={event => setScheduleForm(current => ({ ...current, graceMinutes: event.target.value }))} required /></label>
              <div className="form-actions"><button type="submit" disabled={saving}>{saving ? "Saving…" : "Save schedule"}</button></div>
            </form>
          </section>
          <section className="user-list-section">
            <h3>Configured schedules</h3>
            {schedules.length === 0 ? <p className="muted">No reporting schedules configured. Missing-report alerts remain inactive until a Manager creates one.</p> : <div className="table-wrap"><table className="data-table">
              <thead><tr><th>Report</th><th>Scope</th><th>Frequency</th><th>Deadline</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>{schedules.map(row => <tr key={row.id}><td>{label(row.reportType)}</td><td>{label(row.scopeType)}{row.scopeId ? " · " + row.scopeId : ""}</td><td>{label(row.frequency)}{row.dayOfWeek ? " · day " + row.dayOfWeek : ""}</td><td>{row.dueTime} · {row.graceMinutes}m grace</td><td>{row.enabled ? "Enabled" : "Paused"}</td><td><button type="button" className="secondary-button" disabled={saving} onClick={() => toggleSchedule(row)}>{row.enabled ? "Pause" : "Enable"}</button></td></tr>)}</tbody>
            </table></div>}
          </section>
          <section className="user-list-section">
            <h3>Missing reports <span className="status-pill status-suspended">{missing.length}</span></h3>
            <p className="muted">A missing report is not a zero-activity report. No data is written to operational ledgers by this tracker.</p>
            {missing.length === 0 ? <p className="muted">No overdue reports for the currently configured schedules.</p> : <div className="table-wrap"><table className="data-table">
              <thead><tr><th>Report</th><th>Scope</th><th>Due</th><th>Status</th></tr></thead>
              <tbody>{missing.map((row, index) => <tr key={row.id + row.dueAt + index}><td>{label(row.reportType)}</td><td>{label(row.scopeType)}{row.scopeId ? " · " + row.scopeId : ""}</td><td>{row.dueAt}</td><td><span className="status-pill status-suspended">MISSING</span></td></tr>)}</tbody>
            </table></div>}
          </section>
        </>}

        {activeTab === "notifications" && <section className="user-list-section">
          <h3>Notifications <span className="muted">{unread} unread</span></h3>
          {notifications.length === 0 ? <p className="muted">No notifications yet.</p> : <div className="notification-list">
            {notifications.map(item => <article className={item.readAt ? "notification-item read" : "notification-item unread"} key={item.id}>
              <button type="button" className="notification-open" onClick={() => openNotification(item)}>
                <strong>{item.title}</strong><span>{item.message}</span><small>{item.createdAt} · {label(item.type)}</small>
              </button>
              {!item.readAt && <button type="button" className="text-button" onClick={() => markRead(item)}>Mark read</button>}
            </article>)}
          </div>}
        </section>}
      </section>
      <p className="muted">Emergency alerts are separate from normal reports. If an incident requires an official mortality, medication, inventory, or financial update, submit a normal report and follow the Manager review process.</p>
    </div>
  );
}
