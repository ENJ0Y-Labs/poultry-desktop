import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";

export default function DailyWorkPage() {
  const { batches, dailyForm, setDailyForm, dailyRecord, mortalityForm, setMortalityForm, cullingForm, setCullingForm, saving, saveDailyRecord, recordPopulationEvent } = useContext(FarmManagerContext);
  const activeBatches = batches.filter(batch => batch.status === "ACTIVE");
  const batchOptions = <><option value="">Batch</option>{activeBatches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}</>;

  return (
    <div className="grid">
      <PageSection page="daily-work" tab="daily-record">
        <section id="operations" className="card full">
          <div className="section-head"><h2>Daily record</h2><p className="muted">Record routine observations for one active batch. Population totals come from the backend ledger.</p></div>
          <form className="form-grid" onSubmit={saveDailyRecord}>
            <label>Batch<select required value={dailyForm.batchId} onChange={event => setDailyForm({...dailyForm,batchId:event.target.value})}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={dailyForm.date} onChange={event => setDailyForm({...dailyForm,date:event.target.value})}/></label>
            <label className="full">Daily notes<input value={dailyForm.notes} onChange={event => setDailyForm({...dailyForm,notes:event.target.value})}/></label>
            <button disabled={saving}>Save daily record</button>
          </form>
          {dailyRecord && <div className="subsection"><h3>Saved daily summary · {dailyRecord.date}</h3><div className="table">
            <div className="row header"><span>Birds</span><span>Mortality</span><span>Culling</span><span>Feed entries</span><span>Water</span><span>Notes</span></div>
            <div className="row"><span>{dailyRecord.birds}</span><span>{dailyRecord.mortality}</span><span>{dailyRecord.culling}</span><span>{dailyRecord.feed.length}</span><span>{dailyRecord.totalWaterUnits} units</span><span>{dailyRecord.notes || "—"}</span></div>
          </div><p className="muted">Feed usage is recorded in Feed & Stock and appears here automatically.</p></div>}
        </section>
      </PageSection>

      <PageSection page="daily-work" tab="mortality">
        <section className="card full"><div className="section-head"><h2>Mortality</h2><p className="muted">Record birds that died. Mortality is separate from sales and culling.</p></div>
          <form className="form-grid" onSubmit={event => recordPopulationEvent(event,"MORTALITY")}>
            <label>Batch<select required value={mortalityForm.batchId} onChange={event => setMortalityForm({...mortalityForm,batchId:event.target.value})}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={mortalityForm.date} onChange={event => setMortalityForm({...mortalityForm,date:event.target.value})}/></label>
            <label>Quantity<input required min="1" step="1" type="number" value={mortalityForm.quantity} onChange={event => setMortalityForm({...mortalityForm,quantity:event.target.value})}/></label>
            <label>Reason<input value={mortalityForm.reason} onChange={event => setMortalityForm({...mortalityForm,reason:event.target.value})}/></label>
            <label className="full">Notes<input value={mortalityForm.notes} onChange={event => setMortalityForm({...mortalityForm,notes:event.target.value})}/></label>
            <button disabled={saving}>Record mortality</button>
          </form>
        </section>
      </PageSection>

      <PageSection page="daily-work" tab="culling">
        <section className="card full"><div className="section-head"><h2>Culling</h2><p className="muted">Record birds intentionally removed from the flock. This does not count as mortality.</p></div>
          <form className="form-grid" onSubmit={event => recordPopulationEvent(event,"CULLING")}>
            <label>Batch<select required value={cullingForm.batchId} onChange={event => setCullingForm({...cullingForm,batchId:event.target.value})}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={cullingForm.date} onChange={event => setCullingForm({...cullingForm,date:event.target.value})}/></label>
            <label>Quantity<input required min="1" step="1" type="number" value={cullingForm.quantity} onChange={event => setCullingForm({...cullingForm,quantity:event.target.value})}/></label>
            <label>Reason<input value={cullingForm.reason} onChange={event => setCullingForm({...cullingForm,reason:event.target.value})}/></label>
            <label className="full">Notes<input value={cullingForm.notes} onChange={event => setCullingForm({...cullingForm,notes:event.target.value})}/></label>
            <button disabled={saving}>Record culling</button>
          </form>
        </section>
      </PageSection>

      <PageSection page="daily-work" tab="water">
        <section className="card full"><div className="section-head"><h2>Water usage</h2><p className="muted">Enter configured container sizes and counts, for example 25:4, 75:2. This saves the water figures as part of that date's daily record.</p></div>
          <form className="form-grid" onSubmit={saveDailyRecord}>
            <label>Batch<select required value={dailyForm.batchId} onChange={event => setDailyForm({...dailyForm,batchId:event.target.value})}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={dailyForm.date} onChange={event => setDailyForm({...dailyForm,date:event.target.value})}/></label>
            <label className="full">Water containers<input required value={dailyForm.water} onChange={event => setDailyForm({...dailyForm,water:event.target.value})}/></label>
            <p className="muted">Water is stored in the daily record rather than as a separate transaction. Saving here preserves existing notes for the selected record form.</p>
            <button disabled={saving}>Save water usage</button>
          </form>
        </section>
      </PageSection>
    </div>
  );
}
