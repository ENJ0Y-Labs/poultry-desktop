import { useEffect, useState } from "react";
import { farmApi } from "./services/api.js";

const emptyFarm = { name: "", location: "", timezone: "Africa/Lagos", currency: "NGN" };
const emptyHouse = { name: "", code: "", notes: "" };

export default function App() {
  const [farm, setFarm] = useState(null);
  const [farmForm, setFarmForm] = useState(emptyFarm);
  const [houses, setHouses] = useState([]);
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
      setHouses(await farmApi.listHouses());
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