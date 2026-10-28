import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import FormField from "../components/FormField.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";

export default function SettingsPage() {
  const { signOut, saving, farmForm, setFarmForm, feedTypes, pricingSettings, setPricingSettings, crateSize, setCrateSize, defaultWater, setDefaultWater, waterSizes, setWaterSizes, waterUnitLabel, setWaterUnitLabel, expenseCategories, expenseCategoryForm, setExpenseCategoryForm, account, accountForm, setAccountForm, applicationSettings, setApplicationSettings, backupSettings, setBackupSettings, saveFarm, createExpenseCategory, archiveExpenseCategory, saveAccount, saveApplicationSettings, chooseBackupSettingsDirectory, saveBackupSettings, createBackup, restoreBackup, saveSettings, savePricingSettings } = useContext(FarmManagerContext);

  return (
    <PageTemplate className="grid">
      <PageSection page="settings" tab="farm"><section id="settings" className="card full"><h2>Farm profile</h2><form className="form-grid" onSubmit={saveFarm}>
          <label>Farm name<input required value={farmForm.name} onChange={e=>setFarmForm({...farmForm,name:e.target.value})}/></label>
          <label>Location<input value={farmForm.location} onChange={e=>setFarmForm({...farmForm,location:e.target.value})}/></label>
          <label>Timezone<input required value={farmForm.timezone} onChange={e=>setFarmForm({...farmForm,timezone:e.target.value})}/></label>
          <label>Currency<input required maxLength="3" value={farmForm.currency} onChange={e=>setFarmForm({...farmForm,currency:e.target.value.toUpperCase()})}/></label>
          <button disabled={saving}>Save profile</button>
        </form></section></PageSection>

      <PageSection page="settings" tab="farm-defaults"><section className="card full"><h2>Farm defaults</h2><form className="form-grid" onSubmit={saveSettings}>
          <label>Egg crate size<input type="number" min="1" value={crateSize} onChange={e=>setCrateSize(e.target.value)}/><small>Stored as individual eggs internally.</small></label>
          <label>Default water container<input type="number" min="1" value={defaultWater} onChange={e=>setDefaultWater(e.target.value)}/><small>Must match one configured size.</small></label>
          <label>Water container sizes<input value={waterSizes} onChange={e=>setWaterSizes(e.target.value)}/><small>Comma-separated, e.g. 25, 75.</small></label>
          <label>Water unit label<input required maxLength="20" value={waterUnitLabel} onChange={e=>setWaterUnitLabel(e.target.value)}/><small>Display label only. Existing water figures are not converted. Default: litres.</small></label>
          <button disabled={saving}>Save defaults</button>
        </form></section></PageSection>

      <PageSection page="settings" tab="backup"><section className="card full"><h2>Backup & restore</h2><div className="form-grid"><p className="muted">Backups are created by the backend using SQLite-safe snapshotting. Restore is handled by Electron so the renderer never replaces the database.</p><div className="inline-form"><button type="button" disabled={saving} onClick={createBackup}>Create backup</button><button type="button" disabled={saving || !window.poultryDesktop} onClick={restoreBackup}>Restore backup</button></div><small>Default retention: 30 backup files. A safety copy is created before restore.</small></div></section></PageSection>

      <PageSection page="settings" tab="application"><section className="card full"><div className="section-head"><h2>Configuration</h2><p className="muted">Settings that control farm behavior are stored locally and audited when changed.</p></div>
          <div className="dashboard-columns">
            <div className="subsection"><h3>Feed types</h3><DataTable caption="Feed types" columns={[
          { key: "name", label: "Name" }, { key: "unit", label: "Unit" }, { key: "applicableType", label: "Applies to" }, { key: "status", label: "Status" },
        ]} rows={feedTypes} emptyMessage="No feed types configured." /></div>
            <div className="subsection"><h3>Expense categories</h3><form className="inline-form" onSubmit={createExpenseCategory}><FormField label="New category" required><input required maxLength="80" value={expenseCategoryForm} onChange={e=>setExpenseCategoryForm(e.target.value)}/></FormField><button disabled={saving}>Add</button></form><DataTable caption="Expense categories" columns={[
          { key: "name", label: "Category" }, { key: "status", label: "Status" },
          { key: "action", label: "Action", render: cat => <button type="button" disabled={saving} onClick={() => archiveExpenseCategory(cat.id)}>Archive</button> },
        ]} rows={expenseCategories} emptyMessage="No expense categories configured." /></div>
          </div>
          <div className="dashboard-columns">
            <div className="subsection"><h3>Application settings</h3><form className="form-grid" onSubmit={saveApplicationSettings}><label>Start page<select value={applicationSettings.startPage === "operations" ? "daily-work" : applicationSettings.startPage} onChange={e=>setApplicationSettings({...applicationSettings,startPage:e.target.value})}><option value="dashboard">Dashboard</option><option value="flocks">Flocks</option><option value="daily-work">Daily work</option><option value="feed-stock">Feed & stock</option><option value="health">Health</option><option value="production">Production</option><option value="sales-money">Sales & money</option><option value="reports">Reports</option><option value="settings">Settings</option></select></label><label>Date format<select value={applicationSettings.dateFormat} onChange={e=>setApplicationSettings({...applicationSettings,dateFormat:e.target.value})}><option>YYYY-MM-DD</option><option>DD/MM/YYYY</option><option>DD-MM-YYYY</option></select></label><button disabled={saving}>Save application settings</button></form></div>
            <div className="subsection"><h3>Backup settings</h3><form className="form-grid" onSubmit={saveBackupSettings}><label><input type="checkbox" checked={backupSettings.enabled} onChange={e=>setBackupSettings({...backupSettings,enabled:e.target.checked})}/> Enable automatic backups</label><label>Backup directory<input value={backupSettings.directory} onChange={e=>setBackupSettings({...backupSettings,directory:e.target.value})}/><button type="button" onClick={chooseBackupSettingsDirectory}>Choose folder</button></label><label>Interval (hours)<input type="number" min="1" value={Math.max(1,Math.round(backupSettings.intervalMs/3600000))} onChange={e=>setBackupSettings({...backupSettings,intervalMs:Math.max(1,Number(e.target.value))*3600000})}/></label><button disabled={saving}>Save backup settings</button></form></div>
          </div>
          <div className="subsection"><h3>User / account</h3>{account ? <form className="form-grid" onSubmit={saveAccount}><label>Username<input required minLength="3" maxLength="32" pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,31}" value={accountForm.username} onChange={e=>setAccountForm({...accountForm,username:e.target.value})}/><small>3–32 characters: letters, numbers, dots, underscores, or hyphens.</small></label><label>Full name<input required value={accountForm.fullName} onChange={e=>setAccountForm({...accountForm,fullName:e.target.value})}/></label><label>Email (optional)<input type="email" value={accountForm.email} onChange={e=>setAccountForm({...accountForm,email:e.target.value})}/></label><label>Current password<input type="password" value={accountForm.currentPassword} onChange={e=>setAccountForm({...accountForm,currentPassword:e.target.value})}/></label><label>New password<input type="password" minLength="10" value={accountForm.newPassword} onChange={e=>setAccountForm({...accountForm,newPassword:e.target.value})}/><small>Leave password fields blank to change only profile information.</small></label><button disabled={saving}>Save account</button></form> : <p className="muted">Account unavailable.</p>}</div>
        </section></PageSection>

          <div className="subsection account-session-actions"><h3>Session</h3><p className="muted">Sign out of the current Pomas account on this computer.</p><button type="button" className="danger-button" disabled={saving} onClick={signOut}>{saving ? "Signing out..." : "Sign out"}</button></div>
        </section></PageSection>

      <PageSection page="settings" tab="pricing"><section className="card full"><h2>Pricing & margins</h2><form className="form-grid" onSubmit={savePricingSettings}>
          <label>Target margin %<input type="number" min="0" max="99.999999" step="0.01" value={pricingSettings.targetMarginPercent ?? ""} onChange={e=>setPricingSettings({...pricingSettings,targetMarginPercent:e.target.value})}/><small>Leave empty for no target. A sale never silently lowers it.</small></label>
          <label>Working/latest margin %<input readOnly value={pricingSettings.workingMarginPercent ?? ""}/><small>Updated from actual sale results.</small></label>
          <button disabled={saving}>Save pricing target</button>
        </form></section></PageSection>
    </PageTemplate>
  );
}
