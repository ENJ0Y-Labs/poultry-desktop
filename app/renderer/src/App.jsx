import { useEffect, useState } from "react";
import { batchApi, costApi, farmApi } from "./services/api.js";

const emptyFarm = { name: "", location: "", timezone: "Africa/Lagos", currency: "NGN" };
const emptyHouse = { name: "", code: "", notes: "" };
const emptyBatch = {
  type: "LAYER",
  placementDate: new Date().toISOString().slice(0, 10),
  houseId: "",
  initialBirdCount: "",
  supplierId: "",
  purchaseCostMinor: "",
};
const emptyCost = {
  batchId: "",
  eventDate: new Date().toISOString().slice(0, 10),
  amountMinor: "",
  reason: "",
};

export default function App() {
  const [farm, setFarm] = useState(null);
  const [farmForm, setFarmForm] = useState(emptyFarm);
  const [houses, setHouses] = useState([]);
  const [batches, setBatches] = useState([]);
  const [batchForm, setBatchForm] = useState(emptyBatch);
  const [costForm, setCostForm] = useState(emptyCost);
  const [houseForm, setHouseForm] = useState(emptyHouse);
  const [crateSize, setCrateSize] = useState(30);
  const [defaultWater, setDefaultWater] = useState("");
  const [waterSizes, setWaterSizes] = useState("25, 75");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true); setError("");
    try {
      const currentFarm = await farmApi.get();
      setFarm(currentFarm);
      setFarmForm({name:currentFarm.name,location:currentFarm.location||"",timezone:currentFarm.timezone,currency:currentFarm.currency});
      const settings = await farmApi.getSettings();
      setCrateSize(settings.defaultCrateSize);
      setDefaultWater(settings.defaultWaterContainerSize ?? "");
      setWaterSizes(settings.waterContainerSizes.join(", "));
      const currentHouses = await farmApi.listHouses();
      setHouses(currentHouses);
      setBatchForm(form => ({ ...form, houseId: form.houseId || currentHouses[0]?.id || "" }));
      setBatches(await batchApi.list());
    } catch (err) {
      if (err.message !== "No farm has been created yet.") setError(err.message);
    } finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  async function submitFarm(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await farmApi.create(farmForm);
      setFarm(created);
      const settings = await farmApi.getSettings();
      setCrateSize(settings.defaultCrateSize);
      setDefaultWater(settings.defaultWaterContainerSize ?? "");
      setWaterSizes(settings.waterContainerSizes.join(", "));
      setHouses([]);
      setBatches([]);
      setBatchForm(emptyBatch);
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveFarm(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { setFarm(await farmApi.update(farmForm)); }
    catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveSettings(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const sizes = waterSizes.split(",").map(v => Number(v.trim())).filter(Boolean);
      const updated = await farmApi.updateSettings({
        defaultCrateSize:Number(crateSize),
        defaultWaterContainerSize:defaultWater === "" ? null : Number(defaultWater),
        waterContainerSizes:sizes
      });
      setCrateSize(updated.defaultCrateSize);
      setDefaultWater(updated.defaultWaterContainerSize ?? "");
      setWaterSizes(updated.waterContainerSizes.join(", "));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function createBatch(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await batchApi.create({
        type: batchForm.type,
        placementDate: batchForm.placementDate,
        houseId: batchForm.houseId,
        initialBirdCount: Number(batchForm.initialBirdCount),
        supplierId: batchForm.supplierId.trim() || null,
        purchaseCostMinor: Number(batchForm.purchaseCostMinor),
      });
      setBatches(current => [created, ...current]);
      setBatchForm({ ...emptyBatch, houseId: batchForm.houseId });
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function addBirdCost(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await costApi.add(costForm.batchId, {
        eventDate: costForm.eventDate,
        amountMinor: Number(costForm.amountMinor),
        reason: costForm.reason.trim() || null,
      });
      setCostForm(form => ({ ...emptyCost, batchId: form.batchId }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function markBatchSold(id) {
    setSaving(true); setError("");
    try {
      const updated = await batchApi.markSold(id);
      setBatches(current => current.map(batch => batch.id === id ? updated : batch));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function reopenBatch(id) {
    const reason = window.prompt("Reason for reopening this SOLD batch:");
    if (reason === null) return;
    setSaving(true); setError("");
    try {
      const updated = await batchApi.reopen(id, reason);
      setBatches(current => current.map(batch => batch.id === id ? updated : batch));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function addHouse(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { await farmApi.createHouse(houseForm); setHouseForm(emptyHouse); setHouses(await farmApi.listHouses()); }
    catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  if (loading) return <main className="shell"><p className="muted">Loading farm...</p></main>;

  if (!farm) return (
    <main className="shell narrow">
      <header className="hero"><p className="eyebrow">GRANTINO FARMS</p><h1>Set up your farm</h1><p className="muted">Create the local farm profile first. Everything else hangs from it.</p></header>
      {error && <div className="error">{error}</div>}
      <form className="card form-grid" onSubmit={submitFarm}>
        <label>Farm name<input required value={farmForm.name} onChange={e=>setFarmForm({...farmForm,name:e.target.value})}/></label>
        <label>Location<input value={farmForm.location} onChange={e=>setFarmForm({...farmForm,location:e.target.value})}/></label>
        <label>Timezone<input required value={farmForm.timezone} onChange={e=>setFarmForm({...farmForm,timezone:e.target.value})}/></label>
        <label>Currency<input required maxLength="3" value={farmForm.currency} onChange={e=>setFarmForm({...farmForm,currency:e.target.value.toUpperCase()})}/></label>
        <button disabled={saving} type="submit">{saving ? "Creating..." : "Create farm"}</button>
      </form>
    </main>
  );

  return (
    <main className="shell">
      <header className="topbar"><div><p className="eyebrow">GRANTINO FARMS</p><h1>{farm.name}</h1><p className="muted">{farm.location || "Farm profile"} · {farm.currency} · {farm.timezone}</p></div></header>
      {error && <div className="error">{error}</div>}
      <div className="grid">
        <section className="card"><h2>Farm profile</h2><form className="form-grid" onSubmit={saveFarm}>
          <label>Farm name<input required value={farmForm.name} onChange={e=>setFarmForm({...farmForm,name:e.target.value})}/></label>
          <label>Location<input value={farmForm.location} onChange={e=>setFarmForm({...farmForm,location:e.target.value})}/></label>
          <label>Timezone<input required value={farmForm.timezone} onChange={e=>setFarmForm({...farmForm,timezone:e.target.value})}/></label>
          <label>Currency<input required maxLength="3" value={farmForm.currency} onChange={e=>setFarmForm({...farmForm,currency:e.target.value.toUpperCase()})}/></label>
          <button disabled={saving}>Save profile</button>
        </form></section>

        <section className="card"><h2>Farm defaults</h2><form className="form-grid" onSubmit={saveSettings}>
          <label>Egg crate size<input type="number" min="1" value={crateSize} onChange={e=>setCrateSize(e.target.value)}/><small>Stored as individual eggs internally.</small></label>
          <label>Default water container<input type="number" min="1" value={defaultWater} onChange={e=>setDefaultWater(e.target.value)}/><small>Must match one configured size.</small></label>
          <label>Water container sizes<input value={waterSizes} onChange={e=>setWaterSizes(e.target.value)}/><small>Comma-separated, e.g. 25, 75.</small></label>
          <button disabled={saving}>Save defaults</button>
        </form></section>

        <section className="card full"><div className="section-head"><h2>Batches</h2><p className="muted">Opening a batch creates a permanent flock record and generates its code automatically.</p></div>
          <form className="inline-form" onSubmit={createBatch}>
            <select value={batchForm.type} onChange={e=>setBatchForm({...batchForm,type:e.target.value})}><option value="LAYER">Layer</option><option value="BROILER">Broiler</option></select>
            <input required type="date" value={batchForm.placementDate} onChange={e=>setBatchForm({...batchForm,placementDate:e.target.value})}/>
            <select required value={batchForm.houseId} onChange={e=>setBatchForm({...batchForm,houseId:e.target.value})}><option value="">House / pen</option>{houses.filter(h=>h.status==="ACTIVE").map(h=><option key={h.id} value={h.id}>{h.name} ({h.code})</option>)}</select>
            <input required min="1" type="number" placeholder="Initial birds" value={batchForm.initialBirdCount} onChange={e=>setBatchForm({...batchForm,initialBirdCount:e.target.value})}/>
            <input placeholder="Supplier/source ID (optional)" value={batchForm.supplierId} onChange={e=>setBatchForm({...batchForm,supplierId:e.target.value})}/>
            <input required min="0" step="1" type="number" placeholder="Purchase cost (minor units)" value={batchForm.purchaseCostMinor} onChange={e=>setBatchForm({...batchForm,purchaseCostMinor:e.target.value})}/>
            <button disabled={saving || houses.length===0}>Create batch</button>
          </form>
          <div className="table">
            <div className="row header"><span>Code</span><span>Type</span><span>Placement</span><span>Birds</span><span>Status</span><span>Action</span></div>
            {batches.map(batch=><div className="row" key={batch.id}><span>{batch.code}</span><span>{batch.type}</span><span>{batch.placementDate}</span><span>{batch.initialBirdCount}</span><span>{batch.status}</span><span>{batch.status==="SOLD" ? <button type="button" onClick={()=>reopenBatch(batch.id)} disabled={saving}>Reopen</button> : batch.type==="BROILER" ? <button type="button" onClick={()=>markBatchSold(batch.id)} disabled={saving}>Mark sold</button> : "—"}</span></div>)}
            {batches.length===0 && <p className="muted empty">No batches yet.</p>}
          </div>
          <div className="subsection">
            <div className="section-head"><h3>Additional attributable bird cost</h3><p className="muted">Enter integer minor units only. The backend carries this cost with the live birds.</p></div>
            <form className="inline-form" onSubmit={addBirdCost}>
              <select required value={costForm.batchId} onChange={e=>setCostForm({...costForm,batchId:e.target.value})}>
                <option value="">Batch</option>
                {batches.filter(batch => batch.status === "ACTIVE").map(batch=><option key={batch.id} value={batch.id}>{batch.code}</option>)}
              </select>
              <input required type="date" value={costForm.eventDate} onChange={e=>setCostForm({...costForm,eventDate:e.target.value})}/>
              <input required min="1" step="1" type="number" placeholder="Amount (minor units)" value={costForm.amountMinor} onChange={e=>setCostForm({...costForm,amountMinor:e.target.value})}/>
              <input placeholder="Reason" value={costForm.reason} onChange={e=>setCostForm({...costForm,reason:e.target.value})}/>
              <button disabled={saving || batches.filter(batch => batch.status === "ACTIVE").length===0}>Add cost</button>
            </form>
          </div>
        </section>

        <section className="card full"><div className="section-head"><h2>Houses / pens</h2><p className="muted">Physical locations that batches belong to.</p></div>
          <form className="inline-form" onSubmit={addHouse}><input required placeholder="Name" value={houseForm.name} onChange={e=>setHouseForm({...houseForm,name:e.target.value})}/><input required placeholder="Code" value={houseForm.code} onChange={e=>setHouseForm({...houseForm,code:e.target.value})}/><input placeholder="Notes" value={houseForm.notes} onChange={e=>setHouseForm({...houseForm,notes:e.target.value})}/><button disabled={saving}>Add house</button></form>
          <div className="table"><div className="row header"><span>Name</span><span>Code</span><span>Status</span><span>Notes</span></div>
            {houses.map(h=><div className="row" key={h.id}><span>{h.name}</span><span>{h.code}</span><span>{h.status}</span><span>{h.notes||"—"}</span></div>)}
            {houses.length===0 && <p className="muted empty">No houses or pens yet.</p>}
          </div>
        </section>
      </div>
    </main>
  );
}