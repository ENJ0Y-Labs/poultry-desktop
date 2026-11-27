import { useEffect, useState } from "react";
import { adminUserApi } from "../services/api.js";

const roles = ["WORKER", "ADMINISTRATOR", "MANAGER"];

export default function UserManagementPage({ currentUser }) {
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ username: "", fullName: "", email: "", password: "", role: "WORKER" });
  const [resetPasswords, setResetPasswords] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function refresh() {
    setLoading(true);
    setError("");
    try { setUsers(await adminUserApi.list()); }
    catch (err) { setError(err.message || "User accounts could not be loaded."); }
    finally { setLoading(false); }
  }

  useEffect(() => { refresh(); }, []);

  async function createUser(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await adminUserApi.create({ ...form, email: form.email.trim() || null });
      setForm({ username: "", fullName: "", email: "", password: "", role: "WORKER" });
      setNotice("User account created. Share the credentials securely with the user.");
      await refresh();
    } catch (err) { setError(err.message || "Account could not be created."); }
    finally { setSaving(false); }
  }

  async function changeRole(user, role) {
    if (user.id === currentUser?.id) return;
    setSaving(true);
    setError("");
    try {
      await adminUserApi.changeRole(user.id, role);
      setNotice("Role updated. The user's existing sessions were revoked.");
      await refresh();
    } catch (err) { setError(err.message || "Role could not be changed."); }
    finally { setSaving(false); }
  }

  async function changeStatus(user) {
    if (user.id === currentUser?.id) return;
    const status = user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    setSaving(true);
    setError("");
    try {
      await adminUserApi.changeStatus(user.id, status);
      setNotice(status === "SUSPENDED" ? "Account suspended and sessions revoked." : "Account reactivated.");
      await refresh();
    } catch (err) { setError(err.message || "Account status could not be changed."); }
    finally { setSaving(false); }
  }

  async function resetPassword(user) {
    const password = resetPasswords[user.id] || "";
    if (password.length < 10) {
      setError("A new password must contain at least 10 characters.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await adminUserApi.resetPassword(user.id, password);
      setResetPasswords(current => ({ ...current, [user.id]: "" }));
      setNotice("Password reset. All existing sessions for this account were revoked.");
    } catch (err) { setError(err.message || "Password could not be reset."); }
    finally { setSaving(false); }
  }

  return (
    <div className="user-management-page">
      <section className="card full">
        <div className="section-head"><div><p className="eyebrow">APPLICATION ACCESS</p><h2>User management</h2><p className="muted">Create individual accounts and assign exactly one operational role.</p></div><button type="button" className="secondary-button" onClick={refresh} disabled={loading}>Refresh</button></div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="form-success" role="status">{notice}</p>}
        <section className="user-create-section">
          <h3>Create account</h3>
          <form className="form-grid" onSubmit={createUser}>
            <label>Full name<input value={form.fullName} onChange={event => setForm(current => ({ ...current, fullName: event.target.value }))} required maxLength={120} /></label>
            <label>Username<input value={form.username} onChange={event => setForm(current => ({ ...current, username: event.target.value }))} required minLength={3} maxLength={32} pattern="[a-zA-Z0-9._-]+" /></label>
            <label>Email (optional)<input type="email" value={form.email} onChange={event => setForm(current => ({ ...current, email: event.target.value }))} /></label>
            <label>Temporary password<input type="password" value={form.password} onChange={event => setForm(current => ({ ...current, password: event.target.value }))} required minLength={10} maxLength={200} autoComplete="new-password" /></label>
            <label>Role<select value={form.role} onChange={event => setForm(current => ({ ...current, role: event.target.value }))}>{roles.map(role => <option key={role} value={role}>{role}</option>)}</select></label>
            <div className="form-actions"><button type="submit" disabled={saving}>{saving ? "Creating…" : "Create user"}</button></div>
          </form>
        </section>
        <section className="user-list-section">
          <h3>Existing accounts</h3>
          {loading ? <p className="muted">Loading accounts…</p> : <div className="table-wrap"><table className="data-table">
            <thead><tr><th>User</th><th>Role</th><th>Status</th><th>Reset password</th><th>Access</th></tr></thead>
            <tbody>{users.map(user => <tr key={user.id}>
              <td><strong>{user.fullName}</strong><small>{user.username}{user.email ? " · " + user.email : ""}</small></td>
              <td><select aria-label={"Role for " + user.username} value={user.role} disabled={saving || user.id === currentUser?.id} onChange={event => changeRole(user, event.target.value)}>{roles.map(role => <option key={role} value={role}>{role}</option>)}</select></td>
              <td><span className={"status-pill " + (user.status === "ACTIVE" ? "status-active" : "status-suspended")}>{user.status}</span></td>
              <td><div className="inline-form"><input aria-label={"New password for " + user.username} type="password" minLength={10} placeholder="10+ characters" autoComplete="new-password" value={resetPasswords[user.id] || ""} onChange={event => setResetPasswords(current => ({ ...current, [user.id]: event.target.value }))} /><button type="button" className="secondary-button" disabled={saving || !(resetPasswords[user.id] || "").length} onClick={() => resetPassword(user)}>Reset</button></div></td>
              <td><button type="button" className={user.status === "ACTIVE" ? "danger-button" : "secondary-button"} disabled={saving || user.id === currentUser?.id} onClick={() => changeStatus(user)}>{user.status === "ACTIVE" ? "Suspend" : "Reactivate"}</button></td>
            </tr>)}</tbody>
          </table></div>}
        </section>
        <p className="muted">Role changes and password resets revoke existing sessions. At least one active Administrator must remain. You cannot change or suspend your own account.</p>
      </section>
    </div>
  );
}
