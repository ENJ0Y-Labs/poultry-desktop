import { useContext, useState } from "react";
import ValidatedForm from "../components/ValidatedForm.jsx";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
import { rememberLastBatchId } from "../utils/farmUx.js";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ContextDrawer, { DrawerDetails } from "../components/ContextDrawer.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";

const batchTypeLabel = type => type === "LAYER" ? "Layer" : type === "BROILER" ? "Broiler" : type === "TURKEY" ? "Turkey" : "Unknown type";
const batchStatusLabel = status => status === "ACTIVE" ? "Active" : status === "SOLD" ? "Sold" : String(status || "Unknown").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());

export default function FlocksPage() {
  const [drawerRecord, setDrawerRecord] = useState(null);
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, farm, houses, batches, suppliers, batchForm, setBatchForm, costForm, setCostForm, houseForm, setHouseForm, saving, selectedBatchId, setSelectedBatchId, batchDashboard, createBatch, addBirdCost, markBatchSold, reopenBatch, addHouse, editHouse, editingHouseId, cancelHouseEdit, populationAdjustmentForm, setPopulationAdjustmentForm, populationAdjustments, savePopulationAdjustment, markPopulationEstimated, editingBatchId, batchSetupForm, setBatchSetupForm, saveBatchSetup, editBatchSetup, cancelBatchSetup } = useContext(FarmManagerContext);

  function openBatchDetails(batch) {
    const house = houses.find(item => item.id === batch.houseId);
    const supplier = suppliers.find(item => item.id === batch.supplierId);
    setDrawerRecord({
      kind: "batch",
      batch,
      title: batch.code,
      description: "Batch setup, source information and the available operational snapshot.",
      fields: [
        ["Supplier / source", supplier?.name || "Unknown / not recorded"],
        ["House type", house?.houseType === "TEMPORARY" ? "Temporary" : house ? "Permanent" : null],
        ...(batchDashboard?.batch?.id === batch.id ? [
          ["Current birds", batchDashboard.population?.currentBirds],
          ["Mortality", batchDashboard.population?.mortality],
          ["Culling", batchDashboard.population?.culling],
          ["Sold birds", batchDashboard.population?.sold],
          ["Feed used", formatQuantity(batchDashboard.feed?.totalQuantityMilli)],
          ["Feed cost", formatMoney(batchDashboard.feed?.totalCostMinor, farm.currency)],
          ["Expenses", formatMoney(batchDashboard.expensesMinor, farm.currency)],
          ["Revenue", formatMoney(batchDashboard.revenueMinor, farm.currency)],
          ["Profit", formatMoney(batchDashboard.profitMinor, farm.currency)],
        ] : []),
      ].filter(([, value]) => value !== null && value !== undefined && value !== ""),
    });
  }

  function openHouseDetails(house) {
    const relatedBatches = batches
      .filter(batch => batch.houseId === house.id)
      .sort((a, b) => String(b.placementDate || "").localeCompare(String(a.placementDate || "")));
    setDrawerRecord({
      kind: "house",
      house,
      title: house.name,
      description: "House / pen profile and the flocks assigned to this location.",
      fields: [
        ["Notes", house.notes],
      ],
      relatedBatches,
    });
  }

  return (
    <PageTemplate className="grid">
      <PageSection page="flocks" tab="batch-dashboard"><section id="batch-dashboard" className="card full dashboard-page">
        <div className="section-head"><div><p className="eyebrow">BATCH</p><h2>Batch dashboard</h2></div>
          <select aria-label="Batch dashboard" value={selectedBatchId} onChange={e => { setSelectedBatchId(e.target.value); rememberLastBatchId(e.target.value); }}>
            <option value="">Select a batch</option>{batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code} · {batchTypeLabel(batch.type)}</option>)}
          </select>
        </div>
        {batchDashboard ? <div className="batch-dashboard">
          <div className="metric-grid">
            {[
              ["Initial birds", batchDashboard.population?.initialBirds, ""], ["Current birds", batchDashboard.population?.currentBirds, ""],
              ["Mortality", batchDashboard.population?.mortality, ""], ["Culling", batchDashboard.population?.culling, ""],
              ["Sold", batchDashboard.population?.sold, ""], ["Feed used", formatQuantity(batchDashboard.feed?.totalQuantityMilli), ""],
              ["Feed cost", formatMoney(batchDashboard.feed?.totalCostMinor, farm.currency), ""],
              ["Expenses", formatMoney(batchDashboard.expensesMinor, farm.currency), ""],
              ["Revenue", formatMoney(batchDashboard.revenueMinor, farm.currency), ""],
              ["Profit", formatMoney(batchDashboard.profitMinor, farm.currency), ""]
            ].map(([label,value]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong></div>)}
          </div>
          <div className="notice-banner" role="status"><strong>{batchDashboard.population?.countStatus === "ESTIMATED" ? "Estimated population" : "Verified population"}</strong><span>{batchDashboard.population?.countStatus === "ESTIMATED" ? "Current bird count is provisional. Record a physical count when the flock is counted." : "The current population has a recorded physical count."}</span></div>
          <div className="subsection">
            <h3>Record physical bird count</h3>
            <p className="muted">This records a dated adjustment against the calculated population. It does not change initial birds or rewrite mortality, culling, transfers or sales.</p>
            <ValidatedForm className="inline-form" onSubmit={savePopulationAdjustment}>
              <label className="field"><span>Count date</span><input type="date" required value={populationAdjustmentForm.effectiveDate} min={batchDashboard.batch.placementDate} onChange={e=>setPopulationAdjustmentForm({...populationAdjustmentForm,effectiveDate:e.target.value})}/></label>
              <label className="field"><span>Physical count</span><input type="number" min="0" step="1" required value={populationAdjustmentForm.newCount} onChange={e=>setPopulationAdjustmentForm({...populationAdjustmentForm,newCount:e.target.value})}/></label>
              <label className="field"><span>Reason / notes</span><input required value={populationAdjustmentForm.reason} onChange={e=>setPopulationAdjustmentForm({...populationAdjustmentForm,reason:e.target.value})} placeholder="Physical count completed by farm operator"/></label>
              <button disabled={saving}>Save verified count</button>
            </ValidatedForm>
            <DataTable caption="Physical count adjustment history" columns={[
              {key:"effectiveDate",label:"Effective date"},{key:"previousCount",label:"Previous calculated"},{key:"newCount",label:"Physical count"},
              {key:"difference",label:"Adjustment"},{key:"reason",label:"Reason"},{key:"userId",label:"Recorded by"},{key:"createdAt",label:"Recorded at"}
            ]} rows={populationAdjustments} emptyMessage="No physical count adjustments recorded."/>
          </div>
          <div className="dashboard-columns">
            <div className="subsection"><h3>Production</h3>{batchDashboard.batch.type === "LAYER" ? <div className="dashboard-list">
              <div><span>Good eggs</span><strong>{batchDashboard.eggInventory?.goodCollected ?? "—"}</strong></div>
              <div><span>Cracked eggs</span><strong>{batchDashboard.eggInventory?.cracked ?? "—"}</strong></div>
              <div><span>Egg inventory</span><strong>{batchDashboard.eggInventory?.goodRemaining ?? "—"}</strong></div>
              <div><span>Good egg rate</span><strong>{formatPercent(batchDashboard.eggQuality?.goodRatePercent)}</strong></div>
            </div> : <div className="dashboard-list">
              <div><span>Average weight</span><strong>{formatKg(batchDashboard.broilerGrowth?.currentAverageWeightKg)}</strong></div>
              <div><span>Growth</span><strong>{formatKg(batchDashboard.broilerGrowth?.liveWeightGainKg)}</strong></div>
              <div><span>FCR</span><strong>{formatMetric(batchDashboard.broilerGrowth?.fcr)}</strong></div>
              <div><span>Sales records</span><strong>{batchDashboard.broilerSales?.length ?? 0}</strong></div>
            </div>}</div>
            <div className="subsection"><h3>Status & attention</h3><div className="dashboard-list"><div><span>Status</span><strong>{batchStatusLabel(batchDashboard.batch.status)}</strong></div><div><span>Attention items</span><strong>{batchDashboard.attention}</strong></div></div></div>
          </div>
        </div> : <p className="muted">{selectedBatchId ? "Loading batch dashboard…" : "Select a batch to inspect its dashboard."}</p>}
      </section></PageSection>

      <PageSection page="flocks" tab="flocks"><section id="batches" className="card full"><div className="section-head"><h2>Batches</h2><p className="muted">Opening a batch creates a permanent flock record and generates its code automatically.</p></div>
          <ValidatedForm className="inline-form" onSubmit={createBatch}>
            <label className="field"><span>Batch type</span><select value={batchForm.type} onChange={e=>setBatchForm({...batchForm,type:e.target.value})}><option value="LAYER">Layer</option><option value="BROILER">Broiler</option><option value="TURKEY">Turkey</option></select></label>
            <label className="field"><span>Placement date</span><input required type="date" value={batchForm.placementDate} onChange={e=>setBatchForm({...batchForm,placementDate:e.target.value})}/></label>
            <label className="field"><span>House / pen</span><select required value={batchForm.houseId} onChange={e=>setBatchForm({...batchForm,houseId:e.target.value})}><option value="">House / pen</option>{houses.filter(h=>h.status==="ACTIVE").map(h=><option key={h.id} value={h.id}>{h.name} ({h.code})</option>)}</select></label>
            <label className="field"><span>Initial birds</span><input required min="1" type="number" value={batchForm.initialBirdCount} onChange={e=>setBatchForm({...batchForm,initialBirdCount:e.target.value})}/></label>
            <label className="field"><span>Starting count confidence</span><select value={batchForm.populationCountStatus || "ESTIMATED"} onChange={e=>setBatchForm({...batchForm,populationCountStatus:e.target.value})}><option value="VERIFIED">Verified physical count</option><option value="ESTIMATED">Estimated / provisional count</option></select></label>
            <label className="field"><span>Purchase information</span><select aria-label="Purchase information" value={batchForm.purchaseCostRecorded ? "RECORDED" : "EXISTING_STOCK"} onChange={e=>setBatchForm({...batchForm,purchaseCostRecorded:e.target.value==="RECORDED",purchaseCostMinor:""})}><option value="RECORDED">Record purchase cost</option><option value="EXISTING_STOCK">Not recorded / Existing stock</option></select></label>
            <label className="field"><span>Supplier / source (optional)</span><select value={batchForm.supplierId} onChange={e=>setBatchForm({...batchForm,supplierId:e.target.value})}><option value="">Unknown / not recorded</option>{suppliers.filter(supplier=>supplier.status!=="ARCHIVED").map(supplier=><option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label>
            {batchForm.purchaseCostRecorded && <label className="field"><span>Purchase cost (₦)</span><input required type="text" inputMode="decimal" value={batchForm.purchaseCostMinor} onChange={e=>setBatchForm({...batchForm,purchaseCostMinor:e.target.value})}/></label>}
            <button disabled={saving || houses.length===0}>Create batch</button>
          </ValidatedForm>
          <DataTable caption="Batch records" columns={[
              { key: "code", label: "Code" }, { key: "type", label: "Type", render: batch => batchTypeLabel(batch.type) },
              { key: "houseId", label: "House / pen", render: batch => { const house = houses.find(item => item.id === batch.houseId); return house ? `${house.name} (${house.code})` : batch.houseId || "—"; } },
              { key: "details", label: "Details", render: batch => <button type="button" className="secondary-button" onClick={() => openBatchDetails(batch)}>View</button> },
              { key: "placementDate", label: "Placement" }, { key: "initialBirdCount", label: "Initial birds" }, { key: "populationCountStatus", label: "Count confidence", render: batch => batch.populationCountStatus === "ESTIMATED" ? "Estimated" : "Verified" },
              { key: "confidenceAction", label: "Count action", render: batch => batch.populationCountStatus !== "ESTIMATED" ? <button type="button" className="secondary-button" disabled={saving} onClick={() => markPopulationEstimated(batch.id)}>Mark estimated</button> : "Awaiting physical count" },
              { key: "status", label: "Status", render: batch => batchStatusLabel(batch.status) },
              { key: "action", label: "Action", render: batch => batch.status === "SOLD"
                ? <button type="button" onClick={() => reopenBatch(batch.id)} disabled={saving}>Reopen</button>
                : ["BROILER", "TURKEY"].includes(batch.type) ? <button type="button" onClick={() => markBatchSold(batch.id)} disabled={saving}>Mark sold</button> : "—" },
            ]} rows={batches} emptyMessage="No batches yet." />
          {editingBatchId && <ValidatedForm className="inline-form subsection" onSubmit={saveBatchSetup}>
            <h3>Edit batch setup</h3>
            <p className="muted">Batch code, type, placement date and initial population are permanent. A supplier/source with recorded purchase cost is locked.</p>
            <label className="field"><span>House / pen</span><select required value={batchSetupForm.houseId} onChange={e=>setBatchSetupForm({...batchSetupForm,houseId:e.target.value})}>{houses.filter(h=>h.status==="ACTIVE").map(h=><option key={h.id} value={h.id}>{h.name} ({h.code})</option>)}</select></label>
            <label className="field"><span>Supplier / source</span><select value={batchSetupForm.supplierId} onChange={e=>setBatchSetupForm({...batchSetupForm,supplierId:e.target.value})}><option value="">Unknown / not recorded</option>{suppliers.filter(s=>s.status==="ACTIVE").map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
            <button disabled={saving}>Save setup</button><button type="button" className="secondary-button" onClick={cancelBatchSetup}>Cancel</button>
          </ValidatedForm>}
          <div className="subsection">
            <div className="section-head"><h3>Additional attributable bird cost</h3><p className="muted">Enter the amount in naira. The app converts it to integer kobo before saving.</p></div>
            <ValidatedForm className="inline-form" onSubmit={addBirdCost}>
              <label className="field"><span>Batch</span><select required value={costForm.batchId} onChange={e=>setCostForm({...costForm,batchId:e.target.value})}>
                <option value="">Batch</option>
                {batches.filter(batch => batch.status === "ACTIVE").map(batch=><option key={batch.id} value={batch.id}>{batch.code}</option>)}
              </select></label>
              <label className="field"><span>Cost date</span><input required type="date" value={costForm.eventDate} onChange={e=>setCostForm({...costForm,eventDate:e.target.value})}/></label>
              <label className="field"><span>Amount (₦)</span><input required type="text" inputMode="decimal" value={costForm.amountMinor} onChange={e=>setCostForm({...costForm,amountMinor:e.target.value})}/></label>
              <label className="field"><span>Reason</span><input value={costForm.reason} onChange={e=>setCostForm({...costForm,reason:e.target.value})}/></label>
              <button disabled={saving || batches.filter(batch => batch.status === "ACTIVE").length===0}>Add cost</button>
            </ValidatedForm>
          </div>
        </section></PageSection>

      <PageSection page="flocks" tab="houses"><section id="houses" className="card full"><div className="section-head"><h2>Houses / pens</h2><p className="muted">Physical locations that batches belong to.</p></div>
          <ValidatedForm className="inline-form" onSubmit={addHouse}><label className="field"><span>Name</span><input required value={houseForm.name} onChange={e=>setHouseForm({...houseForm,name:e.target.value})}/></label><label className="field"><span>Code</span><input required value={houseForm.code} onChange={e=>setHouseForm({...houseForm,code:e.target.value})}/></label><label className="field"><span>House type</span><select required value={houseForm.houseType} onChange={e=>setHouseForm({...houseForm,houseType:e.target.value})}><option value="PERMANENT">Permanent</option><option value="TEMPORARY">Temporary</option></select></label><label className="field"><span>Notes</span><input value={houseForm.notes} onChange={e=>setHouseForm({...houseForm,notes:e.target.value})}/></label><button disabled={saving}>{editingHouseId ? "Save house" : "Add house"}</button>{editingHouseId && <button type="button" className="secondary-button" onClick={cancelHouseEdit}>Cancel</button>}</ValidatedForm>
          <DataTable caption="Houses and pens" columns={[
              { key: "name", label: "Name" }, { key: "code", label: "Code" }, { key: "houseType", label: "Type", render: house => house.houseType === "TEMPORARY" ? "Temporary" : "Permanent" }, { key: "status", label: "Status" },
              { key: "occupancy", label: "Occupancy", render: house => batches.some(batch => batch.houseId === house.id && batch.status === "ACTIVE") ? "Occupied" : "Empty" },
              { key: "details", label: "Details", render: house => <button type="button" className="secondary-button" onClick={() => openHouseDetails(house)}>View</button> },
            ]} rows={houses} emptyMessage="No houses or pens yet." />
        </section></PageSection>
      <ContextDrawer
        open={Boolean(drawerRecord)}
        onClose={() => setDrawerRecord(null)}
        title={drawerRecord?.title || "Record details"}
        description={drawerRecord?.description}
        size={drawerRecord?.relatedBatches || drawerRecord?.kind === "batch" ? "large" : "medium"}
        footer={drawerRecord?.kind === "batch" && drawerRecord.batch.status === "ACTIVE" ? <button type="button" onClick={() => {
          editBatchSetup(drawerRecord.batch);
          setDrawerRecord(null);
        }}>Edit batch setup</button> : drawerRecord?.kind === "house" && drawerRecord.house.status === "ACTIVE" ? <button type="button" onClick={() => {
          editHouse(drawerRecord.house);
          setDrawerRecord(null);
        }}>Edit house</button> : null}
      >
        {drawerRecord && <DrawerDetails fields={drawerRecord.fields} />}
        {drawerRecord?.relatedBatches && <section className="subsection" aria-labelledby="house-batch-history-title">
          <h3 id="house-batch-history-title">Batch history</h3>
          {drawerRecord.relatedBatches.length === 0 ? <p className="muted">No batches have been assigned to this house or pen.</p> : <ol className="pomas-drawer-record-list">
            {drawerRecord.relatedBatches.map(batch => <li key={batch.id}>
              <div><strong>{batch.code}</strong><span>{batchTypeLabel(batch.type)} · {batch.placementDate || "Placement date unavailable"}</span></div>
              <strong>{batchStatusLabel(batch.status)}</strong>
            </li>)}
          </ol>}
        </section>}
      </ContextDrawer>
    </PageTemplate>
  );
}
