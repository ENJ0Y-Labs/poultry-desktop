import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import PageSection from "../components/PageSection.jsx";

export default function FeedStockPage() {
  const { formatMoney, farm, batches, feedTypes, feedInventory, feedTypeForm, setFeedTypeForm, feedPurchaseForm, setFeedPurchaseForm, feedUsageForm, setFeedUsageForm, inventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, saving, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, createFeedType, purchaseFeed, useFeed, feedQuantity } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="feed-stock" tab="feed"><section id="feed" className="card full">
          <div className="section-head"><h2>Feed management</h2><p className="muted">Purchases create inventory and one expense. Usage consumes FIFO stock and adds its cost to the batch once.</p></div>
          <div className="subsection">
            <h3>Feed types</h3>
            <form className="inline-form" onSubmit={createFeedType}>
              <input required placeholder="Name e.g. Starter" value={feedTypeForm.name} onChange={e=>setFeedTypeForm({...feedTypeForm,name:e.target.value})}/>
              <input required placeholder="Unit e.g. bag or kg" value={feedTypeForm.unit} onChange={e=>setFeedTypeForm({...feedTypeForm,unit:e.target.value})}/>
              <select value={feedTypeForm.applicableType} onChange={e=>setFeedTypeForm({...feedTypeForm,applicableType:e.target.value})}>
                <option value="BOTH">Both</option><option value="LAYER">Layers</option><option value="BROILER">Broilers</option>
              </select>
              <button disabled={saving}>Add feed type</button>
            </form>
            <div className="table">
              <div className="row header"><span>Name</span><span>Unit</span><span>For</span><span>Status</span></div>
              {feedTypes.map(type=><div className="row" key={type.id}><span>{type.name}</span><span>{type.unit}</span><span>{type.applicableType}</span><span>{type.status}</span></div>)}
              {feedTypes.length===0 && <p className="muted empty">No feed types yet.</p>}
            </div>
          </div>

          <div className="subsection">
            <h3>Purchase feed</h3>
            <form className="inline-form" onSubmit={purchaseFeed}>
              <select required value={feedPurchaseForm.feedTypeId} onChange={e=>{
                const type=feedTypes.find(item=>item.id===e.target.value);
                setFeedPurchaseForm({...feedPurchaseForm,feedTypeId:e.target.value,unit:type?.unit||"bag"});
              }}>
                <option value="">Feed type</option>{feedTypes.filter(t=>t.status==="ACTIVE").map(t=><option key={t.id} value={t.id}>{t.name} ({t.unit})</option>)}
              </select>
              <input required type="date" value={feedPurchaseForm.purchaseDate} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,purchaseDate:e.target.value})}/>
              <input required min="0.001" step="0.001" type="number" placeholder="Quantity" value={feedPurchaseForm.quantity} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,quantity:e.target.value})}/>
              <input placeholder="Supplier/source ID" value={feedPurchaseForm.supplierId} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,supplierId:e.target.value})}/>
              <input required min="1" step="1" type="number" placeholder="Total cost (minor units)" value={feedPurchaseForm.totalCostMinor} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,totalCostMinor:e.target.value})}/>
              <button disabled={saving || feedTypes.filter(t=>t.status==="ACTIVE").length===0}>Record purchase</button>
            </form>
          </div>

          <div className="subsection">
            <h3>Use feed</h3>
            <form className="inline-form" onSubmit={useFeed}>
              <select required value={feedUsageForm.batchId} onChange={e=>setFeedUsageForm({...feedUsageForm,batchId:e.target.value})}>
                <option value="">Batch</option>{batches.filter(b=>b.status==="ACTIVE").map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
              <select required value={feedUsageForm.feedTypeId} onChange={e=>{
                const type=feedTypes.find(item=>item.id===e.target.value);
                setFeedUsageForm({...feedUsageForm,feedTypeId:e.target.value,unit:type?.unit||"bag"});
              }}>
                <option value="">Feed type</option>{feedTypes.filter(t=>t.status==="ACTIVE").map(t=><option key={t.id} value={t.id}>{t.name} ({t.unit})</option>)}
              </select>
              <input required type="date" value={feedUsageForm.usageDate} onChange={e=>setFeedUsageForm({...feedUsageForm,usageDate:e.target.value})}/>
              <input required min="0.001" step="0.001" type="number" placeholder="Quantity" value={feedUsageForm.quantity} onChange={e=>setFeedUsageForm({...feedUsageForm,quantity:e.target.value})}/>
              <input placeholder="Reason" value={feedUsageForm.reason} onChange={e=>setFeedUsageForm({...feedUsageForm,reason:e.target.value})}/>
              <button disabled={saving}>Record usage</button>
            </form>
          </div>

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
              <input required placeholder="Item name" value={inventoryItemForm.name} onChange={e=>setInventoryItemForm({...inventoryItemForm,name:e.target.value})}/>
              <select value={inventoryItemForm.category} onChange={e=>setInventoryItemForm({...inventoryItemForm,category:e.target.value})}>
                <option value="SUPPLY">Farm supply</option>
                <option value="DRUG">Drug</option>
                <option value="VACCINE">Vaccine</option>
              </select>
              <input required placeholder="Unit e.g. bottle, piece, dose" value={inventoryItemForm.unit} onChange={e=>setInventoryItemForm({...inventoryItemForm,unit:e.target.value})}/>
              <input min="0" step="0.001" type="number" placeholder="Reorder level" value={inventoryItemForm.reorderLevel} onChange={e=>setInventoryItemForm({...inventoryItemForm,reorderLevel:e.target.value})}/>
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
                <span>{item.remainingCostMinor}</span>
              </div>)}
              {feedInventory.length===0 && <p className="muted empty">No feed stock yet.</p>}
            </div>
          </div>

          <div className="subsection">
            <h3>Record inventory movement</h3>
            <form className="inline-form" onSubmit={saveInventoryMovement}>
              <select required value={inventoryMovementForm.itemId} onChange={e=>{const id=e.target.value;setInventoryMovementForm({...inventoryMovementForm,itemId:id});loadInventoryMovements(id);}}>
                <option value="">Select item</option>
                {inventoryItems.filter(i=>i.status==="ACTIVE").map(i=><option key={i.id} value={i.id}>{i.name}</option>)}
              </select>
              <input required type="date" value={inventoryMovementForm.date} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,date:e.target.value})}/>
              <select value={inventoryMovementForm.movementType} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,movementType:e.target.value})}>
                <option value="RECEIVE">Receive</option>
                <option value="ISSUE">Issue</option>
                <option value="ADJUST_IN">Adjustment in</option>
                <option value="ADJUST_OUT">Adjustment out</option>
                <option value="WASTE">Waste</option>
              </select>
              <input required min="0.001" step="0.001" type="number" placeholder="Quantity" value={inventoryMovementForm.quantity} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,quantity:e.target.value})}/>
              <input required placeholder="Reason" value={inventoryMovementForm.reason} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,reason:e.target.value})}/>
              <input required placeholder="Source e.g. supplier invoice, treatment" value={inventoryMovementForm.source} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,source:e.target.value})}/>
              <select value={inventoryMovementForm.batchId} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,batchId:e.target.value})}>
                <option value="">Farm-level</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
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
    </div>
  );
}
