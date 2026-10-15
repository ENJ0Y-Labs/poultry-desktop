import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import PageSection from "../components/PageSection.jsx";

export default function DailyWorkPage() {
  const { batches, dailyForm, setDailyForm, dailyRecord, mortalityForm, setMortalityForm, cullingForm, setCullingForm, saving, saveDailyRecord, recordPopulationEvent } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="daily-work" tab="daily-work"><section id="operations" className="card full">
          <div className="section-head">
            <h2>Daily farm operations</h2>
            <p className="muted">Record the day without making workers pretend they measured 143.73 litres of water with a laboratory.</p>
          </div>

          <div className="subsection">
            <h3>Daily record</h3>
            <form className="inline-form" onSubmit={saveDailyRecord}>
              <select required value={dailyForm.batchId} onChange={e=>setDailyForm({...dailyForm,batchId:e.target.value})}>
                <option value="">Batch</option>
                {batches.filter(b=>b.status==="ACTIVE").map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
              <input required type="date" value={dailyForm.date} onChange={e=>setDailyForm({...dailyForm,date:e.target.value})}/>
              <input placeholder="Water e.g. 25:4, 75:2" value={dailyForm.water} onChange={e=>setDailyForm({...dailyForm,water:e.target.value})}/>
              <input placeholder="Daily notes" value={dailyForm.notes} onChange={e=>setDailyForm({...dailyForm,notes:e.target.value})}/>
              <button disabled={saving}>Save daily record</button>
            </form>
            <p className="muted">Water format: container size × count. Example <strong>25:4, 75:2</strong> gives 250 configured units.</p>
          </div>

          <div className="subsection">
            <h3>Mortality</h3>
            <form className="inline-form" onSubmit={e=>recordPopulationEvent(e,"MORTALITY")}>
              <select required value={mortalityForm.batchId} onChange={e=>setMortalityForm({...mortalityForm,batchId:e.target.value})}>
                <option value="">Batch</option>
                {batches.filter(b=>b.status==="ACTIVE").map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
              <input required type="date" value={mortalityForm.date} onChange={e=>setMortalityForm({...mortalityForm,date:e.target.value})}/>
              <input required min="1" type="number" placeholder="Quantity" value={mortalityForm.quantity} onChange={e=>setMortalityForm({...mortalityForm,quantity:e.target.value})}/>
              <input placeholder="Reason" value={mortalityForm.reason} onChange={e=>setMortalityForm({...mortalityForm,reason:e.target.value})}/>
              <input placeholder="Notes" value={mortalityForm.notes} onChange={e=>setMortalityForm({...mortalityForm,notes:e.target.value})}/>
              <button disabled={saving}>Record mortality</button>
            </form>
          </div>

          <div className="subsection">
            <h3>Culling</h3>
            <form className="inline-form" onSubmit={e=>recordPopulationEvent(e,"CULLING")}>
              <select required value={cullingForm.batchId} onChange={e=>setCullingForm({...cullingForm,batchId:e.target.value})}>
                <option value="">Batch</option>
                {batches.filter(b=>b.status==="ACTIVE").map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
              <input required type="date" value={cullingForm.date} onChange={e=>setCullingForm({...cullingForm,date:e.target.value})}/>
              <input required min="1" type="number" placeholder="Quantity" value={cullingForm.quantity} onChange={e=>setCullingForm({...cullingForm,quantity:e.target.value})}/>
              <input placeholder="Reason" value={cullingForm.reason} onChange={e=>setCullingForm({...cullingForm,reason:e.target.value})}/>
              <input placeholder="Notes" value={cullingForm.notes} onChange={e=>setCullingForm({...cullingForm,notes:e.target.value})}/>
              <button disabled={saving}>Record culling</button>
            </form>
          </div>

          {dailyRecord && (
            <div className="subsection">
              <h3>Daily summary · {dailyRecord.date}</h3>
              <div className="table">
                <div className="row header"><span>Birds</span><span>Mortality</span><span>Culling</span><span>Feed entries</span><span>Water</span><span>Notes</span></div>
                <div className="row">
                  <span>{dailyRecord.birds}</span>
                  <span>{dailyRecord.mortality}</span>
                  <span>{dailyRecord.culling}</span>
                  <span>{dailyRecord.feed.length}</span>
                  <span>{dailyRecord.totalWaterUnits} units</span>
                  <span>{dailyRecord.notes || "—"}</span>
                </div>
              </div>
              <p className="muted">Feed usage is recorded through Feed management and appears here automatically. Birds are calculated from the population ledger.</p>
            </div>
          )}
        </section></PageSection>
    </div>
  );
}
