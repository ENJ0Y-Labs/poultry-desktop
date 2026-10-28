import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
import { rememberLastBatchId } from "../utils/farmUx.js";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";

const batchTypeLabel = type => type === "LAYER" ? "Layer" : type === "BROILER" ? "Broiler" : "Unknown type";
const batchStatusLabel = status => status === "ACTIVE" ? "Active" : status === "SOLD" ? "Sold" : String(status || "Unknown").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());

export default function FlocksPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, farm, houses, batches, suppliers, batchForm, setBatchForm, costForm, setCostForm, houseForm, setHouseForm, saving, selectedBatchId, setSelectedBatchId, batchDashboard, createBatch, addBirdCost, markBatchSold, reopenBatch, addHouse } = useContext(FarmManagerContext);

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
          <form className="inline-form" onSubmit={createBatch}>
            <label className="field"><span>Batch type</span><select value={batchForm.type} onChange={e=>setBatchForm({...batchForm,type:e.target.value})}><option value="LAYER">Layer</option><option value="BROILER">Broiler</option></select></label>
            <label className="field"><span>Placement date</span><input required type="date" value={batchForm.placementDate} onChange={e=>setBatchForm({...batchForm,placementDate:e.target.value})}/></label>
            <label className="field"><span>House / pen</span><select required value={batchForm.houseId} onChange={e=>setBatchForm({...batchForm,houseId:e.target.value})}><option value="">House / pen</option>{houses.filter(h=>h.status==="ACTIVE").map(h=><option key={h.id} value={h.id}>{h.name} ({h.code})</option>)}</select></label>
            <label className="field"><span>Initial birds</span><input required min="1" type="number" value={batchForm.initialBirdCount} onChange={e=>setBatchForm({...batchForm,initialBirdCount:e.target.value})}/></label>
            <label className="field"><span>Supplier / source (optional)</span><select value={batchForm.supplierId} onChange={e=>setBatchForm({...batchForm,supplierId:e.target.value})}><option value="">Unknown / not recorded</option>{suppliers.filter(supplier=>supplier.status!=="ARCHIVED").map(supplier=><option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label>
            <label className="field"><span>Purchase cost (₦)</span><input required type="text" inputMode="decimal" value={batchForm.purchaseCostMinor} onChange={e=>setBatchForm({...batchForm,purchaseCostMinor:e.target.value})}/></label>
            <button disabled={saving || houses.length===0}>Create batch</button>
          </form>
          <DataTable caption="Batch records" columns={[
              { key: "code", label: "Code" }, { key: "type", label: "Type", render: batch => batchTypeLabel(batch.type) },
              { key: "placementDate", label: "Placement" }, { key: "initialBirdCount", label: "Birds" },
              { key: "status", label: "Status", render: batch => batchStatusLabel(batch.status) },
              { key: "action", label: "Action", render: batch => batch.status === "SOLD"
                ? <button type="button" onClick={() => reopenBatch(batch.id)} disabled={saving}>Reopen</button>
                : batch.type === "BROILER" ? <button type="button" onClick={() => markBatchSold(batch.id)} disabled={saving}>Mark sold</button> : "—" },
            ]} rows={batches} emptyMessage="No batches yet." />
          <div className="subsection">
            <div className="section-head"><h3>Additional attributable bird cost</h3><p className="muted">Enter the amount in naira. The app converts it to integer kobo before saving.</p></div>
            <form className="inline-form" onSubmit={addBirdCost}>
              <label className="field"><span>Batch</span><select required value={costForm.batchId} onChange={e=>setCostForm({...costForm,batchId:e.target.value})}>
                <option value="">Batch</option>
                {batches.filter(batch => batch.status === "ACTIVE").map(batch=><option key={batch.id} value={batch.id}>{batch.code}</option>)}
              </select></label>
              <label className="field"><span>Cost date</span><input required type="date" value={costForm.eventDate} onChange={e=>setCostForm({...costForm,eventDate:e.target.value})}/></label>
              <label className="field"><span>Amount (₦)</span><input required type="text" inputMode="decimal" value={costForm.amountMinor} onChange={e=>setCostForm({...costForm,amountMinor:e.target.value})}/></label>
              <label className="field"><span>Reason</span><input value={costForm.reason} onChange={e=>setCostForm({...costForm,reason:e.target.value})}/></label>
              <button disabled={saving || batches.filter(batch => batch.status === "ACTIVE").length===0}>Add cost</button>
            </form>
          </div>
        </section></PageSection>

      <PageSection page="flocks" tab="houses"><section id="houses" className="card full"><div className="section-head"><h2>Houses / pens</h2><p className="muted">Physical locations that batches belong to.</p></div>
          <form className="inline-form" onSubmit={addHouse}><label className="field"><span>Name</span><input required value={houseForm.name} onChange={e=>setHouseForm({...houseForm,name:e.target.value})}/></label><label className="field"><span>Code</span><input required value={houseForm.code} onChange={e=>setHouseForm({...houseForm,code:e.target.value})}/></label><label className="field"><span>Notes</span><input value={houseForm.notes} onChange={e=>setHouseForm({...houseForm,notes:e.target.value})}/></label><button disabled={saving}>Add house</button></form>
          <DataTable caption="Houses and pens" columns={[
              { key: "name", label: "Name" }, { key: "code", label: "Code" }, { key: "status", label: "Status" },
              { key: "notes", label: "Notes", render: house => house.notes || "—" },
            ]} rows={houses} emptyMessage="No houses or pens yet." />
        </section></PageSection>
    </PageTemplate>
  );
}
