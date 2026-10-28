import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
// eslint-disable-next-line no-unused-vars
import EmptyState from "../components/EmptyState.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";

export default function HealthPage() {
  const { formatMoney, farm, batches, healthForm, setHealthForm, drugForm, setDrugForm, vaccinationForm, setVaccinationForm, healthRecords, drugRecords, vaccinationRecords, saving, saveHealth, saveDrug, saveVaccination, loadHealthForBatch } = useContext(FarmManagerContext);
  const batchOptions = <><option value="">Batch</option>{batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}</>;

  return (
    <PageTemplate className="grid">
      {batches.length === 0 && <EmptyState className="empty-state" role="status">No batches yet. Create a batch in <a href="#flocks/flocks">Flocks → Batches</a> before recording health, treatment, or vaccination details.</EmptyState>}
      <PageSection page="health" tab="health-record">
        <section id="health" className="card full"><div className="section-head"><h2>Health records</h2><p className="muted">Record symptoms, conditions, and action taken. Treatments and vaccinations are tracked separately.</p></div>
          <form className="form-grid" onSubmit={saveHealth}>
            <label>Batch<select required value={healthForm.batchId} onChange={event => {setHealthForm({...healthForm,batchId:event.target.value});loadHealthForBatch(event.target.value);}}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={healthForm.date} onChange={event => setHealthForm({...healthForm,date:event.target.value})}/></label>
            <label>Condition / problem<input required value={healthForm.conditionProblem} onChange={event => setHealthForm({...healthForm,conditionProblem:event.target.value})}/></label>
            <label>Description<input required value={healthForm.description} onChange={event => setHealthForm({...healthForm,description:event.target.value})}/></label>
            <label className="full">Action taken<input required value={healthForm.action} onChange={event => setHealthForm({...healthForm,action:event.target.value})}/></label>
            <button disabled={saving}>Record health issue</button>
          </form>
          <DataTable caption="Health records" columns={[
              { key: "recordDate", label: "Date" }, { key: "conditionProblem", label: "Condition" },
              { key: "description", label: "Description" }, { key: "action", label: "Action" },
            ]} rows={healthRecords} emptyMessage="Select a batch to view health records." />
        </section>
      </PageSection>

      <PageSection page="health" tab="drugs">
        <section className="card full"><div className="section-head"><h2>Drugs & treatments</h2><p className="muted">Each drug record creates one shared DRUGS expense. Do not record the same cost again as a separate expense.</p></div>
          <form className="form-grid" onSubmit={saveDrug}>
            <label>Batch<select required value={drugForm.batchId} onChange={event => {setDrugForm({...drugForm,batchId:event.target.value});loadHealthForBatch(event.target.value);}}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={drugForm.date} onChange={event => setDrugForm({...drugForm,date:event.target.value})}/></label>
            <label>Drug<input required value={drugForm.drug} onChange={event => setDrugForm({...drugForm,drug:event.target.value})}/></label>
            <label>Quantity<input required min="1" step="1" type="number" value={drugForm.quantity} onChange={event => setDrugForm({...drugForm,quantity:event.target.value})}/></label>
            <label>Cost (₦)<input required type="text" inputMode="decimal" value={drugForm.costMinor} onChange={event => setDrugForm({...drugForm,costMinor:event.target.value})}/></label>
            <label>Reason<input required value={drugForm.reason} onChange={event => setDrugForm({...drugForm,reason:event.target.value})}/></label>
            <button disabled={saving}>Record drug</button>
          </form>
          <DataTable caption="Drug and treatment records" columns={[
              { key: "recordDate", label: "Date" }, { key: "drug", label: "Drug" }, { key: "quantity", label: "Quantity" },
              { key: "costMinor", label: "Cost", render: row => formatMoney(row.costMinor, farm.currency) }, { key: "reason", label: "Reason" },
            ]} rows={drugRecords} emptyMessage="No drug records for the selected batch." />
        </section>
      </PageSection>

      <PageSection page="health" tab="vaccination">
        <section className="card full"><div className="section-head"><h2>Vaccination</h2><p className="muted">Keep vaccine schedule and administration history separate from illness and drug records.</p></div>
          <form className="form-grid" onSubmit={saveVaccination}>
            <label>Batch<select required value={vaccinationForm.batchId} onChange={event => {setVaccinationForm({...vaccinationForm,batchId:event.target.value});loadHealthForBatch(event.target.value);}}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={vaccinationForm.date} onChange={event => setVaccinationForm({...vaccinationForm,date:event.target.value})}/></label>
            <label>Vaccine<input required value={vaccinationForm.vaccine} onChange={event => setVaccinationForm({...vaccinationForm,vaccine:event.target.value})}/></label>
            <label>Dose<input required value={vaccinationForm.dose} onChange={event => setVaccinationForm({...vaccinationForm,dose:event.target.value})}/></label>
            <label>Quantity<input required min="1" step="1" type="number" value={vaccinationForm.quantity} onChange={event => setVaccinationForm({...vaccinationForm,quantity:event.target.value})}/></label>
            <label>Notes<input value={vaccinationForm.notes} onChange={event => setVaccinationForm({...vaccinationForm,notes:event.target.value})}/></label>
            <button disabled={saving}>Record vaccination</button>
          </form>
          <DataTable caption="Vaccination records" columns={[
              { key: "recordDate", label: "Date" }, { key: "vaccine", label: "Vaccine" }, { key: "dose", label: "Dose" },
              { key: "quantity", label: "Quantity" }, { key: "notes", label: "Notes", render: row => row.notes || "—" },
            ]} rows={vaccinationRecords} emptyMessage="No vaccination records for the selected batch." />
        </section>
      </PageSection>
    </PageTemplate>
  );
}
