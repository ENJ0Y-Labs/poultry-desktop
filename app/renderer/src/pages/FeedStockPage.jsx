import { useContext } from "react";
import { feedApi } from "../services/api.js";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";

export default function FeedStockPage() {
  const { formatMoney, farm, batches, feedTypes, setFeedTypes, feedInventory, feedTypeForm, setFeedTypeForm, setError, setSaving, feedPurchaseForm, setFeedPurchaseForm, inventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, suppliers, supplierForm, setSupplierForm, saveSupplier, archiveSupplier, saving, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, createFeedType, purchaseFeed, feedQuantity } = useContext(FarmManagerContext);

  async function saveConversion(event, type) {
    event.preventDefault();
    const raw = event.currentTarget.elements.kgPerUnit.value.trim();
    const kgPerUnit = raw === "" ? null : Number(raw);
    if (kgPerUnit !== null && (!Number.isFinite(kgPerUnit) || kgPerUnit <= 0)) {
      setError("Kilograms per unit must be greater than zero, or left blank when unknown.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const updated = await feedApi.updateConversion(type.id, kgPerUnit);
      setFeedTypes(current => current.map(item => item.id === updated.id ? updated : item));
    } catch (error) {
      setError(error.message || "Could not update feed conversion.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="grid">
      <PageSection page="feed-stock" tab="feed"><section id="feed" className="card full">
          <div className="section-head"><h2>Feed management</h2><p className="muted">Purchases create inventory and one expense. Usage consumes FIFO stock and adds its cost to the batch once. FCR converts feed usage to kilograms using each feed type’s configured kg-per-unit factor. Leave unknown units unset until the farm confirms the package weight.</p></div>
          <div className="subsection">
            <h3>Feed types</h3>
            <form className="inline-form" onSubmit={createFeedType}>
              <label className="field"><span>Name e.g. Starter</span><input required value={feedTypeForm.name} onChange={e=>setFeedTypeForm({...feedTypeForm,name:e.target.value})}/></label>
              <label className="field"><span>Unit e.g. bag or kg</span><input required value={feedTypeForm.unit} onChange={e=>setFeedTypeForm({...feedTypeForm,unit:e.target.value,kgPerUnit:e.target.value.trim().toLowerCase()==="kg"?"1":feedTypeForm.kgPerUnit})}/></label>
              <label className="field"><span>Kg per unit (leave blank if unknown)</span><input min="0.001" step="0.001" type="number" value={feedTypeForm.unit.trim().toLowerCase()==="kg"?"1":feedTypeForm.kgPerUnit} disabled={feedTypeForm.unit.trim().toLowerCase()==="kg"} onChange={e=>setFeedTypeForm({...feedTypeForm,kgPerUnit:e.target.value})}/></label>
              <label className="field"><span>Applicable to</span><select value={feedTypeForm.applicableType} onChange={e=>setFeedTypeForm({...feedTypeForm,applicableType:e.target.value})}>
                <option value="BOTH">Both</option><option value="LAYER">Layers</option><option value="BROILER">Broilers</option>
              </select></label>
              <button disabled={saving}>Add feed type</button>
            </form>
            <div className="table">
              <div className="row header"><span>Name</span><span>Unit</span><span>For</span><span>Kg per unit</span><span>Conversion setup</span><span>Status</span></div>
              {feedTypes.map(type=><div className="row" key={type.id}><span>{type.name}</span><span>{type.unit}</span><span>{type.applicableType}</span><span>{type.kgPerUnit == null ? "Unknown" : `${type.kgPerUnit} kg`}</span><span>{type.status === "ACTIVE" ? <form className="inline-form" onSubmit={event=>saveConversion(event,type)}><label className="field"><span>Kg per {type.unit}</span><input name="kgPerUnit" aria-label={`Kilograms per ${type.unit} for ${type.name}`} type="number" min="0.001" step="0.001" defaultValue={type.kgPerUnit ?? ""} placeholder="Unknown" disabled={type.unit.trim().toLowerCase()==="kg"} /></label><button disabled={saving || type.unit.trim().toLowerCase()==="kg"}>Save conversion</button></form> : "Archived"}</span><span>{type.status}</span></div>)}
              {feedTypes.length===0 && <p className="muted empty">No feed types yet.</p>}
            </div>
          </div>

          <p className="muted">Record daily feed consumption in Daily Work. Feed usage still updates this inventory through the existing FIFO ledger.</p>

          <div className="subsection">
            <h3>Feed inventory</h3>
            <div className="table">
              <div className="row header"><span>Feed</span><span>Purchased</span><span>Used</span><span>Remaining</span><span>Remaining cost</span></div>
              {feedInventory.map(item=><div className="row" key={item.feedTypeId}>
                <span>{item.feedTypeName}</span><span>{feedQuantity(item.purchasedQuantityMilli)} {item.unit}</span><span>{feedQuantity(item.consumedQuantityMilli)} {item.unit}</span><span>{feedQuantity(item.remainingQuantityMilli)} {item.unit}</span><span>{formatMoney(item.remainingCostMinor, farm.currency)}</span>
              </div>)}
              {feedInventory.length===0 && <p className="muted empty">No feed inventory yet.</p>}
            </div>
          </div>
        </section></PageSection>

      <PageSection page="feed-stock" tab="inventory"><section id="inventory" className="card full">
          <div className="section-head">
            <h2>Inventory</h2>
            <p className="muted">Track drugs, vaccines, and farm supplies with explicit stock movements. Feed remains in the dedicated Feed module because its FIFO cost ledger is authoritative.</p>
          </div>

          <div className="subsection">
            <h3>Inventory items</h3>
            <form className="inline-form" onSubmit={saveInventoryItem}>
              <label className="field"><span>Item name</span><input required value={inventoryItemForm.name} onChange={e=>setInventoryItemForm({...inventoryItemForm,name:e.target.value})}/></label>
              <label className="field"><span>Category</span><select value={inventoryItemForm.category} onChange={e=>setInventoryItemForm({...inventoryItemForm,category:e.target.value})}>
                <option value="SUPPLY">Farm supply</option>
                <option value="DRUG">Drug</option>
                <option value="VACCINE">Vaccine</option>
              </select></label>
              <label className="field"><span>Unit e.g. bottle, piece, dose</span><input required value={inventoryItemForm.unit} onChange={e=>setInventoryItemForm({...inventoryItemForm,unit:e.target.value})}/></label>
              <label className="field"><span>Reorder level</span><input min="0" step="0.001" type="number" value={inventoryItemForm.reorderLevel} onChange={e=>setInventoryItemForm({...inventoryItemForm,reorderLevel:e.target.value})}/></label>
              <button disabled={saving}>Add item</button>
            </form>
            <div className="table">
              <div className="row header"><span>Item</span><span>Category</span><span>Stock</span><span>Reorder</span><span>Unit</span><span>Status</span></div>
              {inventoryItems.map(row=><div className="row" key={row.id}>
                <span>{row.name}</span><span>{row.category}</span><span>{row.quantityOnHand}</span><span>{row.reorderLevel}</span><span>{row.unit}</span><span>{row.status}</span>
              </div>)}
              {inventoryItems.length===0 && <p className="muted empty">No non-feed inventory items yet.</p>}
            </div>
          </div>

          <div className="subsection">
            <h3>Feed stock</h3>
            <p className="muted">Feed is shown here from the dedicated FIFO feed ledger. Do not create a second generic item for feed.</p>
            <div className="table">
              <div className="row header"><span>Feed</span><span>Remaining</span><span>Used</span><span>Remaining cost</span></div>
              {feedInventory.map(item=><div className="row" key={item.feedTypeId}>
                <span>{item.feedTypeName}</span>
                <span>{feedQuantity(item.remainingQuantityMilli)} {item.unit}</span>
                <span>{feedQuantity(item.consumedQuantityMilli)} {item.unit}</span>
                <span>{formatMoney(item.remainingCostMinor, farm.currency)}</span>
              </div>)}
              {feedInventory.length===0 && <p className="muted empty">No feed stock yet.</p>}
            </div>
          </div>

          <div className="subsection">
            <h3>Record inventory movement</h3>
            <form className="inline-form" onSubmit={saveInventoryMovement}>
              <label className="field"><span>Inventory item</span><select required value={inventoryMovementForm.itemId} onChange={e=>{const id=e.target.value;setInventoryMovementForm({...inventoryMovementForm,itemId:id});loadInventoryMovements(id);}}>
                <option value="">Select item</option>
                {inventoryItems.filter(i=>i.status==="ACTIVE").map(i=><option key={i.id} value={i.id}>{i.name}</option>)}
              </select></label>
              <label className="field"><span>Movement date</span><input required type="date" value={inventoryMovementForm.date} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,date:e.target.value})}/></label>
              <label className="field"><span>Movement type</span><select value={inventoryMovementForm.movementType} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,movementType:e.target.value})}>
                <option value="RECEIVE">Receive</option>
                <option value="ISSUE">Issue</option>
                <option value="ADJUST_IN">Adjustment in</option>
                <option value="ADJUST_OUT">Adjustment out</option>
                <option value="WASTE">Waste</option>
              </select></label>
              <label className="field"><span>Quantity</span><input required min="0.001" step="0.001" type="number" value={inventoryMovementForm.quantity} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,quantity:e.target.value})}/></label>
              <label className="field"><span>Reason</span><input required value={inventoryMovementForm.reason} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,reason:e.target.value})}/></label>
              <label className="field"><span>Source e.g. supplier invoice, treatment</span><input required value={inventoryMovementForm.source} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,source:e.target.value})}/></label>
              <label className="field"><span>Batch association</span><select value={inventoryMovementForm.batchId} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,batchId:e.target.value})}>
                <option value="">Farm-level</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select></label>
              <button disabled={saving || !inventoryMovementForm.itemId}>Record movement</button>
            </form>
            {inventoryMovementForm.itemId && <div className="table">
              <div className="row header"><span>Date</span><span>Movement</span><span>Quantity</span><span>Reason</span><span>Source</span><span>Batch</span></div>
              {inventoryMovements.map(row=><div className="row" key={row.id}>
                <span>{row.movementDate}</span><span>{row.movementType}</span><span>{row.quantity} {row.category==="SUPPLY" ? "" : ""}</span><span>{row.reason}</span><span>{row.source}</span><span>{row.batchId ? (batches.find(b=>b.id===row.batchId)?.code || row.batchId) : "Farm"}</span>
              </div>)}
              {inventoryMovements.length===0 && <p className="muted empty">No movements for this item.</p>}
            </div>}
          </div>
        </section></PageSection>
      <PageSection page="feed-stock" tab="purchases"><section id="purchases" className="card full"><div className="section-head"><h2>Feed purchases</h2><p className="muted">Choose a registered supplier or leave it blank if the source is unknown.</p></div>
          <div className="subsection">
            <h3>Purchase feed</h3>
            <form className="inline-form" onSubmit={purchaseFeed}>
              <label className="field"><span>Feed type</span><select required value={feedPurchaseForm.feedTypeId} onChange={e=>{
                const type=feedTypes.find(item=>item.id===e.target.value);
                setFeedPurchaseForm({...feedPurchaseForm,feedTypeId:e.target.value,unit:type?.unit||"bag"});
              }}>
                <option value="">Feed type</option>{feedTypes.filter(t=>t.status==="ACTIVE").map(t=><option key={t.id} value={t.id}>{t.name} ({t.unit})</option>)}
              </select></label>
              <label className="field"><span>Purchase date</span><input required type="date" value={feedPurchaseForm.purchaseDate} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,purchaseDate:e.target.value})}/></label>
              <label className="field"><span>Quantity</span><input required min="0.001" step="0.001" type="number" value={feedPurchaseForm.quantity} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,quantity:e.target.value})}/></label>
              <label className="field"><span>Supplier (optional)</span><select value={feedPurchaseForm.supplierId} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,supplierId:e.target.value})}><option value="">No supplier recorded</option>{suppliers.filter(supplier=>supplier.status!=="ARCHIVED").map(supplier=><option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label>
              <label className="field"><span>Total cost (₦)</span><input required type="text" inputMode="decimal" value={feedPurchaseForm.totalCostMinor} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,totalCostMinor:e.target.value})}/></label>
              <button disabled={saving || feedTypes.filter(t=>t.status==="ACTIVE").length===0}>Record purchase</button>
            </form>
          </div>

      </section></PageSection>

      <PageSection page="feed-stock" tab="suppliers"><section id="suppliers" className="card full">
        <div className="section-head"><h2>Suppliers</h2><p className="muted">Maintain reusable supplier records for feed purchases. Archiving keeps historical purchase references intact.</p></div>
        <form className="form-grid" onSubmit={saveSupplier}>
          <label>Supplier name<input required maxLength="120" value={supplierForm.name} onChange={event=>setSupplierForm({...supplierForm,name:event.target.value})}/></label>
          <label>Phone<input maxLength="40" value={supplierForm.phone} onChange={event=>setSupplierForm({...supplierForm,phone:event.target.value})}/></label>
          <label className="full">Notes<input value={supplierForm.notes} onChange={event=>setSupplierForm({...supplierForm,notes:event.target.value})}/></label>
          <button disabled={saving}>Add supplier</button>
        </form>
        <div className="table"><div className="row header"><span>Supplier</span><span>Phone</span><span>Notes</span><span>Status</span><span>Action</span></div>
          {suppliers.map(supplier=><div className="row" key={supplier.id}><span>{supplier.name}</span><span>{supplier.phone||"—"}</span><span>{supplier.notes||"—"}</span><span>{supplier.status||"ACTIVE"}</span><span>{supplier.status==="ARCHIVED" ? "Archived" : <button type="button" disabled={saving} onClick={()=>archiveSupplier(supplier.id)}>Archive</button>}</span></div>)}
          {suppliers.length===0 && <p className="muted empty">No suppliers recorded yet.</p>}
        </div>
      </section></PageSection>

    </div>
  );
}
