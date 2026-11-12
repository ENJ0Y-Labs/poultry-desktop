import { useContext } from "react";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ValidatedForm from "../components/ValidatedForm.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import OptionalFields from "../components/OptionalFields.jsx";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import { ModalForm } from "../components/Modal.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import PageTemplate from "../components/PageTemplate.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import EmptyState from "../components/EmptyState.jsx";

export default function DailyWorkPage() {
  const { error } = useContext(FarmManagerContext);
  const {
    batches, dailyForm, setDailyForm, emptyDaily, emptyFeedUsage, emptyPopulationEvent, dailyRecord, dailyRecordLoading, waterUnitLabel,
    mortalityForm, setMortalityForm, mortalityModalSignal, feedUsageModalSignal, cullingForm, setCullingForm, saving,
    saveDailyRecord, recordPopulationEvent, feedTypes, feedUsageForm, setFeedUsageForm,
    useFeed, feedQuantity, dataLoadError, retryData,
  } = useContext(FarmManagerContext);
  const activeBatches = batches.filter(batch => batch.status === "ACTIVE");
  const selectedBatch = activeBatches.find(batch => batch.id === dailyForm.batchId);
  const applicableFeedTypes = feedTypes.filter(type =>
    type.status === "ACTIVE" && selectedBatch && (type.applicableTypes || []).includes(selectedBatch.type)
  );
  const batchOptions = <><option value="">Batch</option>{activeBatches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}</>;
  const hasSavedActivity = Boolean(dailyRecord && (dailyRecord.id || dailyRecord.feed?.length || dailyRecord.water?.length || dailyRecord.populationEvents?.length || dailyRecord.notes || dailyRecord.mortality || dailyRecord.culling));

  function updateDailyBatch(batchId) {
    setDailyForm(form => ({ ...form, batchId }));
    const nextBatch = activeBatches.find(batch => batch.id === batchId);
    const nextApplicableTypes = feedTypes.filter(type =>
      type.status === "ACTIVE" && nextBatch && (type.applicableTypes || []).includes(nextBatch.type)
    );
    setFeedUsageForm(form => ({
      ...form,
      batchId,
      feedTypeId: nextApplicableTypes.some(type => type.id === form.feedTypeId) ? form.feedTypeId : "",
      unit: nextApplicableTypes.find(type => type.id === form.feedTypeId)?.unit || nextApplicableTypes[0]?.unit || "bag",
    }));
  }

  function updateDailyDate(date) {
    setDailyForm(form => ({ ...form, date }));
    setFeedUsageForm(form => ({ ...form, usageDate: date }));
  }

  return (
    <PageTemplate className="grid pomas-page page-daily-work">
      {activeBatches.length === 0 && <EmptyState className="empty-state" role="status">No active batches are available. Create a batch in <a href="#flocks/flocks">Flocks → Batches</a> before recording daily work.</EmptyState>}
      <PageSection page="daily-work" tab="daily-record">
        <section id="operations" className="card full">
          <div className="section-head"><h2>Daily record</h2><p className="muted">Choose the batch and farm date once, then record feed usage and daily notes. Population totals come from the backend ledger.</p></div>
          <ModalForm title="Daily record" description="Save the batch's daily notes for the selected date." triggerLabel="Save daily notes" loading={saving} error={error} onDiscard={() => setDailyForm(form => ({ ...emptyDaily, batchId: form.batchId }))}>
<ValidatedForm className="form-grid" onSubmit={event => saveDailyRecord(event, "notes")}>
            <label>Batch<select required value={dailyForm.batchId} onChange={event => updateDailyBatch(event.target.value)}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={dailyForm.date} onChange={event => updateDailyDate(event.target.value)}/></label>
            <OptionalFields title="Daily notes" description="Add context that may help explain the day’s activity."><label className="full">Daily notes<input value={dailyForm.notes} onChange={event => setDailyForm({...dailyForm,notes:event.target.value})}/></label></OptionalFields>
            <button disabled={saving}>Save daily notes</button>
          </ValidatedForm>
</ModalForm>

          <div className="subsection">
            <h3>Feed usage</h3>
            <p className="muted">Record the feed given to this batch for the selected date. This uses the existing feed ledger, deducts stock, and allocates FIFO cost once.</p>
            <ModalForm title="Feed usage" description="Record feed issued to the selected batch. Stock and FIFO costing remain managed by the backend." triggerLabel="Record feed usage" openSignal={feedUsageModalSignal} loading={saving} error={error} onDiscard={() => setFeedUsageForm(form => ({ ...emptyFeedUsage, batchId: form.batchId }))}>
<ValidatedForm className="form-grid" onSubmit={useFeed}>
              <label>Feed type<select required value={feedUsageForm.feedTypeId} onChange={event => {
                const type = feedTypes.find(item => item.id === event.target.value);
                setFeedUsageForm(form => ({...form,feedTypeId:event.target.value,unit:type?.unit||"bag"}));
              }}>
                <option value="">Feed type</option>{applicableFeedTypes.map(type => <option key={type.id} value={type.id}>{type.name}{type.brand ? ` · ${type.brand}` : ""} ({type.unit})</option>)}
              </select></label>
              <label>Quantity<input required min="0.001" step="0.001" type="number" value={feedUsageForm.quantity} onChange={event => setFeedUsageForm({...feedUsageForm,quantity:event.target.value})}/></label>
              <OptionalFields title="Additional details"><label className="full">Reason (optional)<input value={feedUsageForm.reason} onChange={event => setFeedUsageForm({...feedUsageForm,reason:event.target.value})} placeholder="e.g. Morning feeding"/></label></OptionalFields>
              <button disabled={saving || !dailyForm.batchId || applicableFeedTypes.length === 0}>Record feed usage</button>
            </ValidatedForm>
</ModalForm>
            {applicableFeedTypes.length === 0 && <p className="muted">No active feed types are configured for this batch's bird type. Add an applicable feed type in Feed & Stock first.</p>}
          </div>

          <div className="subsection">
            <h3>Saved activity · {dailyForm.date}</h3>
            {dailyRecordLoading ? <EmptyState variant="loading" heading="Loading saved activity" compact /> : dataLoadError ? <EmptyState variant="error" description={dataLoadError} onRetry={retryData} compact /> : hasSavedActivity ? <>
              <DataTable caption="Saved daily activity" columns={[
                  { key: "birds", label: "Birds" }, { key: "mortality", label: "Mortality" }, { key: "culling", label: "Culling" },
                  { key: "water", label: "Water" }, { key: "notes", label: "Notes" },
                ]} rows={[{ id: "daily-activity", birds: dailyRecord.birds, mortality: dailyRecord.mortality, culling: dailyRecord.culling, water: `${dailyRecord.totalWaterUnits} ${waterUnitLabel || "litres"}`, notes: dailyRecord.notes || "—" }]} />
              <h4>Feed recorded</h4>
              {dailyRecord.feed?.length ? <ul>{dailyRecord.feed.map((entry, index) => <li key={entry.id || entry.usageId || index}>{entry.feedTypeName || entry.feedName || "Feed"}{entry.brand ? ` · ${entry.brand}` : ""}: {feedQuantity(entry.quantityMilli ?? entry.quantityUsedMilli ?? 0)} {entry.unit || ""}</li>)}</ul> : <p className="muted">No feed usage recorded for this date.</p>}
            </> : <EmptyState variant="no-records" heading="No daily record for this date" description="Record feed usage, water or daily notes to begin this day’s activity." compact />}
          </div>
        </section>
      </PageSection>

      <PageSection page="daily-work" tab="mortality">
        <section className="card full"><div className="section-head"><h2>Mortality</h2><p className="muted">Record birds that died. Mortality is separate from sales and culling.</p></div>
          <ModalForm title="Record mortality" description="Record birds that died. This entry updates the batch population ledger." triggerLabel="Record mortality" openSignal={mortalityModalSignal} loading={saving} error={error} onDiscard={() => setMortalityForm(form => ({ ...emptyPopulationEvent, batchId: form.batchId }))}>
<ValidatedForm className="form-grid" onSubmit={event => recordPopulationEvent(event,"MORTALITY")}>
            <label>Batch<select required value={mortalityForm.batchId} onChange={event => setMortalityForm({...mortalityForm,batchId:event.target.value})}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={mortalityForm.date} onChange={event => setMortalityForm({...mortalityForm,date:event.target.value})}/></label>
            <label>Quantity<input required min="1" step="1" type="number" value={mortalityForm.quantity} onChange={event => setMortalityForm({...mortalityForm,quantity:event.target.value})}/></label>
            <OptionalFields title="Mortality details" description="Optional reason and notes. The batch, date and quantity remain the required record."><label>Reason<input value={mortalityForm.reason} onChange={event => setMortalityForm({...mortalityForm,reason:event.target.value})}/></label>
              <label className="full">Notes<input value={mortalityForm.notes} onChange={event => setMortalityForm({...mortalityForm,notes:event.target.value})}/></label></OptionalFields>
            <button disabled={saving}>Record mortality</button>
          </ValidatedForm>
</ModalForm>
        </section>
      </PageSection>

      <PageSection page="daily-work" tab="culling">
        <section className="card full"><div className="section-head"><h2>Culling</h2><p className="muted">Record birds intentionally removed from the flock. This does not count as mortality.</p></div>
          <ModalForm title="Record culling" description="Record birds intentionally removed from the flock." triggerLabel="Record culling" loading={saving} error={error} onDiscard={() => setCullingForm(form => ({ ...emptyPopulationEvent, batchId: form.batchId }))}>
<ValidatedForm className="form-grid" onSubmit={event => recordPopulationEvent(event,"CULLING")}>
            <label>Batch<select required value={cullingForm.batchId} onChange={event => setCullingForm({...cullingForm,batchId:event.target.value})}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={cullingForm.date} onChange={event => setCullingForm({...cullingForm,date:event.target.value})}/></label>
            <label>Quantity<input required min="1" step="1" type="number" value={cullingForm.quantity} onChange={event => setCullingForm({...cullingForm,quantity:event.target.value})}/></label>
            <label>Reason<input value={cullingForm.reason} onChange={event => setCullingForm({...cullingForm,reason:event.target.value})}/></label>
            <label className="full">Notes<input value={cullingForm.notes} onChange={event => setCullingForm({...cullingForm,notes:event.target.value})}/></label>
            <button disabled={saving}>Record culling</button>
          </ValidatedForm>
</ModalForm>
        </section>
      </PageSection>

      <PageSection page="daily-work" tab="water">
        <section className="card full"><div className="section-head"><h2>Water usage</h2><p className="muted">Enter configured container sizes and counts, for example 25:4, 75:2. Values use the farm unit label ({waterUnitLabel || "litres"}); no conversion is applied. This saves water figures as part of that date's daily record.</p></div>
          <ModalForm title="Water usage" description="Save the selected batch's water-container readings for this date." triggerLabel="Save water usage" loading={saving} error={error} onDiscard={() => setDailyForm(form => ({ ...emptyDaily, batchId: form.batchId }))}>
<ValidatedForm className="form-grid" onSubmit={event => saveDailyRecord(event, "water")}>
            <label>Batch<select required value={dailyForm.batchId} onChange={event => updateDailyBatch(event.target.value)}>{batchOptions}</select></label>
            <label>Date<input required type="date" value={dailyForm.date} onChange={event => updateDailyDate(event.target.value)}/></label>
            <label className="full">Water containers ({waterUnitLabel || "litres"})<input required value={dailyForm.water} onChange={event => setDailyForm({...dailyForm,water:event.target.value})}/></label>
            <p className="muted">Water is stored in the daily record rather than as a separate transaction. Saving here preserves existing notes.</p>
            <button disabled={saving}>Save water usage</button>
          </ValidatedForm>
</ModalForm>
        </section>
      </PageSection>
    </PageTemplate>
  );
}
