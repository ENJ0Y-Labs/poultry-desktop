import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import EmptyState from "../components/EmptyState.jsx";
// eslint-disable-next-line no-unused-vars

export default function DailyWorkPage() {
  const {
    batches, dailyForm, setDailyForm, dailyRecord, dailyRecordLoading, waterUnitLabel,
    mortalityForm, setMortalityForm, cullingForm, setCullingForm, saving,
    saveDailyRecord, recordPopulationEvent, feedTypes, feedUsageForm, setFeedUsageForm,
    useFeed, feedQuantity,
  } = useContext(FarmManagerContext);
  const activeBatches = batches.filter(batch => batch.status === "ACTIVE");
  const batchOptions = <><option value="">Batch</option>{activeBatches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}</>;
  const hasSavedActivity = Boolean(dailyRecord && (dailyRecord.id || dailyRecord.feed?.length || dailyRecord.water?.length || dailyRecord.populationEvents?.length || dailyRecord.notes || dailyRecord.mortality || dailyRecord.culling));

  function updateDailyBatch(batchId) {
    setDailyForm(form => ({ ...form, batchId }));
    setFeedUsageForm(form => ({ ...form, batchId }));
  }

  function updateDailyDate(date) {
    setDailyForm(form => ({ ...form, date }));
    setFeedUsageForm(form => ({ ...form, usageDate: date }));
  }

  return (
    <div className="grid">
      {activeBatches.length === 0 && <EmptyState className="empty-state" role="status">No active batches are available. Create a batch in <a href="#flocks/flocks">Flocks → Batches</a> before recording daily work.</EmptyState>}
      <PageSection page="daily-work" tab="daily-record">
        <section id="operations" className="card full">
          <div className="section-head"><h2>Daily record</h2><p className="muted">Choose the batch and farm date once, then record feed usage and daily notes. Population totals come from the backend ledger.</p></div>
          <form className="form-grid" onSubmit={event => saveDailyRecord(event, "notes")}>
            <label>Batch<select required value={dailyForm.batchId} onChange={event => updateDailyBatch(event.target.value)}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={dailyForm.date} onChange={event => updateDailyDate(event.target.value)}/></label>
            <label className="full">Daily notes<input value={dailyForm.notes} onChange={event => setDailyForm({...dailyForm,notes:event.target.value})}/></label>
            <button disabled={saving}>Save daily notes</button>
          </form>

          <div className="subsection">
            <h3>Feed usage</h3>
            <p className="muted">Record the feed given to this batch for the selected date. This uses the existing feed ledger, deducts stock, and allocates FIFO cost once.</p>
            <form className="form-grid" onSubmit={useFeed}>
              <label>Feed type<select required value={feedUsageForm.feedTypeId} onChange={event => {
                const type = feedTypes.find(item => item.id === event.target.value);
                setFeedUsageForm(form => ({...form,feedTypeId:event.target.value,unit:type?.unit||"bag"}));
              }}>
                <option value="">Feed type</option>{feedTypes.filter(type => type.status === "ACTIVE").map(type => <option key={type.id} value={type.id}>{type.name} ({type.unit})</option>)}
              </select></label>
              <label>Quantity<input required min="0.001" step="0.001" type="number" value={feedUsageForm.quantity} onChange={event => setFeedUsageForm({...feedUsageForm,quantity:event.target.value})}/></label>
              <label className="full">Reason (optional)<input value={feedUsageForm.reason} onChange={event => setFeedUsageForm({...feedUsageForm,reason:event.target.value})} placeholder="e.g. Morning feeding"/></label>
              <button disabled={saving || !dailyForm.batchId || feedTypes.filter(type => type.status === "ACTIVE").length === 0}>Record feed usage</button>
            </form>
            {feedTypes.filter(type => type.status === "ACTIVE").length === 0 && <p className="muted">Set up a feed type in Feed & Stock before recording feed usage.</p>}
          </div>

          <div className="subsection">
            <h3>Saved activity · {dailyForm.date}</h3>
            {dailyRecordLoading ? <p className="muted" role="status">Loading saved activity…</p> : hasSavedActivity ? <>
              <DataTable caption="Saved daily activity" columns={[
                  { key: "birds", label: "Birds" }, { key: "mortality", label: "Mortality" }, { key: "culling", label: "Culling" },
                  { key: "water", label: "Water" }, { key: "notes", label: "Notes" },
                ]} rows={[{ id: "daily-activity", birds: dailyRecord.birds, mortality: dailyRecord.mortality, culling: dailyRecord.culling, water: `${dailyRecord.totalWaterUnits} ${waterUnitLabel || "litres"}`, notes: dailyRecord.notes || "—" }]} />
              <h4>Feed recorded</h4>
              {dailyRecord.feed?.length ? <ul>{dailyRecord.feed.map((entry, index) => <li key={entry.id || entry.usageId || index}>{entry.feedTypeName || entry.feedName || "Feed"}: {feedQuantity(entry.quantityMilli ?? entry.quantityUsedMilli ?? 0)} {entry.unit || ""}</li>)}</ul> : <p className="muted">No feed usage recorded for this date.</p>}
            </> : <p className="muted">No saved daily record for this batch and date yet. Record feed usage or save daily notes to begin.</p>}
          </div>
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
        <section className="card full"><div className="section-head"><h2>Water usage</h2><p className="muted">Enter configured container sizes and counts, for example 25:4, 75:2. Values use the farm unit label ({waterUnitLabel || "litres"}); no conversion is applied. This saves water figures as part of that date's daily record.</p></div>
          <form className="form-grid" onSubmit={event => saveDailyRecord(event, "water")}>
            <label>Batch<select required value={dailyForm.batchId} onChange={event => updateDailyBatch(event.target.value)}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={dailyForm.date} onChange={event => updateDailyDate(event.target.value)}/></label>
            <label className="full">Water containers ({waterUnitLabel || "litres"})<input required value={dailyForm.water} onChange={event => setDailyForm({...dailyForm,water:event.target.value})}/></label>
            <p className="muted">Water is stored in the daily record rather than as a separate transaction. Saving here preserves existing notes.</p>
            <button disabled={saving}>Save water usage</button>
          </form>
        </section>
      </PageSection>
    </div>
  );
}
