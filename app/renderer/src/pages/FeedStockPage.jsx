import { useContext, useEffect, useRef, useState } from "react";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ValidatedForm from "../components/ValidatedForm.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import OptionalFields from "../components/OptionalFields.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import MultiStepForm from "../components/MultiStepForm.jsx";
import { feedApi } from "../services/api.js";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import { ConfirmAction, ModalForm } from "../components/Modal.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ContextDrawer, { DrawerDetails } from "../components/ContextDrawer.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
// eslint-disable-next-line no-unused-vars
import { InventoryAnalytics } from "../components/AnalyticsCharts.jsx";

export default function FeedStockPage() {
  const { error } = useContext(FarmManagerContext);
  const [inventoryItemOpenSignal, setInventoryItemOpenSignal] = useState(0);
  const [supplierOpenSignal, setSupplierOpenSignal] = useState(0);
  const { dataLoadError, retryData, setDataLoadError, formatMoney, farm, dateRange, setDateRange, applicationSettings, batches, feedTypes, setFeedTypes, feedInventory, feedTypeForm, setFeedTypeForm, setError, setSaving, feedPurchaseForm, setFeedPurchaseForm, inventoryItems, inventoryItemForm, setInventoryItemForm, emptyInventoryItem, emptyInventoryMovement, emptyFeedPurchase, emptySupplier, feedPurchaseModalSignal, editingInventoryItemId, editInventoryItem, cancelInventoryItemEdit, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, suppliers, supplierForm, setSupplierForm, editingSupplierId, editSupplier, cancelSupplierEdit, saveSupplier, archiveSupplier, saving, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, createFeedType, purchaseFeed, feedQuantity } = useContext(FarmManagerContext);
  const [openingStockForm, setOpeningStockForm] = useState({
    feedTypeId: "", effectiveDate: "", quantity: "", reason: "",
  });
  const [openingBalances, setOpeningBalances] = useState([]);
  const [openingBalancesLoading, setOpeningBalancesLoading] = useState(false);
  const [editingFeedType, setEditingFeedType] = useState(null);
  const [drawerRecord, setDrawerRecord] = useState(null);
  const [drawerMovementItemId, setDrawerMovementItemId] = useState("");
  const drawerMovementRequestRef = useRef(0);

  function feedProductLabel(type) {
    const brand = type.brand ? ` · ${type.brand}` : "";
    return `${type.name}${brand} (${type.unit})`;
  }

  function openInventoryDetails(item) {
    const requestId = ++drawerMovementRequestRef.current;
    setDrawerMovementItemId("");
    setDrawerRecord({
      kind: "inventory",
      item,
      title: item.name,
      description: "Inventory profile and recorded stock movements.",
      fields: [
        ["Reorder level", `${item.reorderLevel ?? "—"} ${item.unit || ""}`.trim()],
        ["Unit", item.unit],
      ].filter(([, value]) => value !== null && value !== undefined && value !== ""),
    });
    Promise.resolve(loadInventoryMovements(item.id)).finally(() => {
      if (drawerMovementRequestRef.current === requestId) setDrawerMovementItemId(item.id);
    });
  }

  function openSupplierDetails(supplier) {
    drawerMovementRequestRef.current += 1;
    const relatedBatches = batches.filter(batch => batch.supplierId === supplier.id);
    setDrawerRecord({
      kind: "supplier",
      supplier,
      title: supplier.name,
      description: "Supplier contact profile and batches that reference this supplier as their source.",
      fields: [
        ["Phone", supplier.phone],
        ["Email", supplier.email],
        ["Address", supplier.address],
        ["Notes", supplier.notes],
      ].filter(([, value]) => value !== null && value !== undefined && value !== ""),
      relatedBatches,
    });
  }

  async function saveFeedProduct(event) {
    event.preventDefault();
    if (!editingFeedType) return;
    const packageWeightKg = editingFeedType.packageWeightKg.trim() === ""
      ? null : Number(editingFeedType.packageWeightKg);
    if (packageWeightKg !== null && (!Number.isFinite(packageWeightKg) || packageWeightKg <= 0)) {
      setError("Package weight must be greater than zero, or left blank when unknown.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const updated = await feedApi.updateType(editingFeedType.id, {
        name: editingFeedType.name.trim(),
        brand: editingFeedType.brand.trim() || null,
        packagingName: editingFeedType.packagingName.trim() || null,
        packageWeightKg,
      });
      setFeedTypes(current => current.map(item => item.id === updated.id ? updated : item)
        .sort((a, b) => a.name.localeCompare(b.name) || (a.brand || "").localeCompare(b.brand || "")));
      setEditingFeedType(null);
    } catch (error) {
      setError(error.message || "Could not update feed product details.");
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => {
    let active = true;
    if (!feedTypes.length) {
      setOpeningBalances([]);
      setOpeningBalancesLoading(false);
      return () => { active = false; };
    }
    setOpeningBalancesLoading(true);
    Promise.all(feedTypes.map(type => feedApi.openingBalances(type.id)))
      .then(results => {
        if (active) setOpeningBalances(results.flat());
      })
      .catch(error => {
        if (active) {
          setDataLoadError(error.message || "Could not load opening-stock history.");
          setError(error.message || "Could not load opening-stock history.");
        }
      })
      .finally(() => {
        if (active) setOpeningBalancesLoading(false);
      });
    return () => { active = false; };
  }, [feedTypes, setError, setDataLoadError]);

  async function recordOpeningStock(event) {
    event.preventDefault();
    const type = feedTypes.find(item => item.id === openingStockForm.feedTypeId);
    if (!type) {
      setError("Select a feed type before recording opening stock.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await feedApi.createOpeningBalance({
        feedTypeId: type.id,
        effectiveDate: openingStockForm.effectiveDate,
        quantity: Number(openingStockForm.quantity),
        unit: type.unit,
        reason: openingStockForm.reason.trim(),
      });
      const history = await feedApi.openingBalances(type.id);
      setOpeningBalances(current => [
        ...current.filter(item => item.feedTypeId !== type.id),
        ...history,
      ]);
      setOpeningStockForm(current => ({ ...current, quantity: "", reason: "" }));
    } catch (error) {
      setError(error.message || "Could not record opening stock.");
    } finally {
      setSaving(false);
    }
  }

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
    <PageTemplate className="grid pomas-page page-feed-stock">
      <PageSection page="feed-stock" tab="feed"><section id="feed" className="card full">
          <div className="section-head"><h2>Feed management</h2><p className="muted">Purchases create inventory and one expense. Usage consumes FIFO stock and adds its cost to the batch once. FCR converts feed usage to kilograms using each feed type’s configured kg-per-unit factor. Leave unknown units unset until the farm confirms the package weight.</p></div>
          <div className="subsection">
            <h3>Feed types</h3>
            <p className="muted">A feed product is identified by its feed name and brand. The same feed name can be registered under different brands. Editing product details keeps its existing purchases, opening stock, usage and FIFO history attached to the same record.</p>
            <ValidatedForm className="inline-form" onSubmit={createFeedType}>
              <label className="field"><span>Name e.g. Starter</span><input required value={feedTypeForm.name} onChange={e=>setFeedTypeForm({...feedTypeForm,name:e.target.value})}/></label>
              <label className="field"><span>Brand (optional)</span><input value={feedTypeForm.brand} onChange={e=>setFeedTypeForm({...feedTypeForm,brand:e.target.value})}/></label>
              <label className="field"><span>Package name</span><input value={feedTypeForm.packagingName} onChange={e=>setFeedTypeForm({...feedTypeForm,packagingName:e.target.value})}/></label>
              <label className="field"><span>Package weight in kg (leave blank if unknown)</span><input min="0.001" step="0.001" type="number" value={feedTypeForm.packageWeightKg} onChange={e=>setFeedTypeForm({...feedTypeForm,packageWeightKg:e.target.value})}/></label>
              <label className="field"><span>Unit e.g. bag or kg</span><input required value={feedTypeForm.unit} onChange={e=>setFeedTypeForm({...feedTypeForm,unit:e.target.value,kgPerUnit:e.target.value.trim().toLowerCase()==="kg"?"1":feedTypeForm.kgPerUnit})}/></label>
              <label className="field"><span>Kg per unit (leave blank if unknown)</span><input min="0.001" step="0.001" type="number" value={feedTypeForm.unit.trim().toLowerCase()==="kg"?"1":feedTypeForm.kgPerUnit} disabled={feedTypeForm.unit.trim().toLowerCase()==="kg"} onChange={e=>setFeedTypeForm({...feedTypeForm,kgPerUnit:e.target.value})}/></label>
              <fieldset className="field applicability-selector">
                <legend>Applicable to</legend>
                {[
                  ["LAYER", "Layers"],
                  ["BROILER", "Broilers"],
                  ["TURKEY", "Turkeys"],
                ].map(([type, label]) => (
                  <label key={type}>
                    <input
                      type="checkbox"
                      checked={(feedTypeForm.applicableTypes || []).includes(type)}
                      onChange={event => setFeedTypeForm({
                        ...feedTypeForm,
                        applicableTypes: event.target.checked
                          ? [...(feedTypeForm.applicableTypes || []), type]
                          : (feedTypeForm.applicableTypes || []).filter(item => item !== type),
                      })}
                    />
                    {label}
                  </label>
                ))}
              </fieldset>
              <button disabled={saving || !(feedTypeForm.applicableTypes || []).length}>Add feed type</button>
            </ValidatedForm>
            {editingFeedType && <ValidatedForm className="inline-form" aria-label="Edit feed product" onSubmit={saveFeedProduct}>
              <h4>Edit feed product</h4>
              <label className="field"><span>Product name</span><input required value={editingFeedType.name} onChange={event => setEditingFeedType({ ...editingFeedType, name: event.target.value })}/></label>
              <label className="field"><span>Brand</span><input value={editingFeedType.brand} onChange={event => setEditingFeedType({ ...editingFeedType, brand: event.target.value })}/></label>
              <label className="field"><span>Package name</span><input value={editingFeedType.packagingName} onChange={event => setEditingFeedType({ ...editingFeedType, packagingName: event.target.value })}/></label>
              <label className="field"><span>Package weight in kg (leave blank if unknown)</span><input type="number" min="0.001" step="0.001" value={editingFeedType.packageWeightKg} onChange={event => setEditingFeedType({ ...editingFeedType, packageWeightKg: event.target.value })}/></label>
              <button disabled={saving || !editingFeedType.name.trim()}>Save product details</button>
              <button type="button" className="secondary" disabled={saving} onClick={() => setEditingFeedType(null)}>Cancel</button>
            </ValidatedForm>}
            <DataTable caption="Feed types and unit conversions" columns={[
              { key: "name", label: "Product" }, { key: "brand", label: "Brand", render: type => type.brand || "—" }, { key: "packagingName", label: "Package", render: type => type.packagingName || "—" }, { key: "packageWeightKg", label: "Package weight", render: type => type.packageWeightKg == null ? "Unknown" : `${type.packageWeightKg} kg` }, { key: "unit", label: "Unit" }, { key: "applicableTypes", label: "For", render: type => (type.applicableTypes || []).map(value => ({ LAYER: "Layers", BROILER: "Broilers", TURKEY: "Turkeys" }[value] || value)).join(", ") || "—" },
              { key: "kgPerUnit", label: "Kg per unit", render: type => type.kgPerUnit == null ? "Unknown" : `${type.kgPerUnit} kg` },
              { key: "conversion", label: "Conversion setup", render: type => type.status === "ACTIVE" ? <ValidatedForm className="inline-form" onSubmit={event => saveConversion(event, type)}><label className="field"><span>Kg per {type.unit}</span><input name="kgPerUnit" aria-label={`Kilograms per ${type.unit} for ${type.name}`} type="number" min="0.001" step="0.001" defaultValue={type.kgPerUnit ?? ""} placeholder="Unknown" disabled={type.unit.trim().toLowerCase() === "kg"} /></label><button disabled={saving || type.unit.trim().toLowerCase() === "kg"}>Save conversion</button></ValidatedForm> : "Archived" },
              { key: "status", label: "Status" },
              { key: "actions", label: "Actions", render: type => <button type="button" className="secondary" disabled={saving} onClick={() => setEditingFeedType({
                id: type.id, name: type.name, brand: type.brand || "", packagingName: type.packagingName || "",
                packageWeightKg: type.packageWeightKg == null ? "" : String(type.packageWeightKg),
              })}>Edit details</button> },
            ]} rows={feedTypes} emptyMessage="No feed types yet. Configure a feed product using the form above before recording purchases or usage." />
          </div>

          <div className="subsection">
            <h3>Add opening stock</h3>
            <p className="muted">Record feed already on the farm or received without a purchase. This creates an unvalued FIFO stock lot only. It does not create a supplier, purchase, invoice, or feed expense. If it is consumed, the affected feed cost is shown as unknown rather than ₦0.</p>
            <ValidatedForm className="inline-form" onSubmit={recordOpeningStock}>
              <label className="field"><span>Feed type</span><select required value={openingStockForm.feedTypeId} onChange={event => setOpeningStockForm({ ...openingStockForm, feedTypeId: event.target.value })}>
                <option value="">Select feed type</option>
                {feedTypes.filter(type => type.status === "ACTIVE").map(type => <option key={type.id} value={type.id}>{feedProductLabel(type)}</option>)}
              </select></label>
              <label className="field"><span>Effective date</span><input required type="date" value={openingStockForm.effectiveDate} onChange={event => setOpeningStockForm({ ...openingStockForm, effectiveDate: event.target.value })}/></label>
              <label className="field"><span>Quantity</span><input required min="0.001" step="0.001" type="number" value={openingStockForm.quantity} onChange={event => setOpeningStockForm({ ...openingStockForm, quantity: event.target.value })}/></label>
              <label className="field"><span>Reason / source</span><input required maxLength="500" value={openingStockForm.reason} onChange={event => setOpeningStockForm({ ...openingStockForm, reason: event.target.value })} placeholder="e.g. Physical stock count at setup"/></label>
              <button disabled={saving || !openingStockForm.feedTypeId}>Record opening stock</button>
            </ValidatedForm>
            <DataTable caption="Opening stock history" columns={[
              { key: "effectiveDate", label: "Effective date" },
              { key: "feedTypeName", label: "Feed" },
              { key: "brand", label: "Brand", render: item => item.brand || "—" },
              { key: "quantityMilli", label: "Quantity", render: item => `${feedQuantity(item.quantityMilli)} ${item.unit}` },
              { key: "reason", label: "Reason / source" },
              { key: "valuation", label: "Valuation", render: () => "Unvalued opening stock" },
            ]} rows={openingBalances} emptyMessage="No opening stock recorded yet. Enter an opening balance above if the farm already has feed on hand." loading={openingBalancesLoading} error={dataLoadError} onRetry={retryData} />
          </div>

          <p className="muted">Record daily feed consumption in Daily Work. Feed usage still updates this inventory through the existing FIFO ledger.</p>

          <div className="subsection">
            <h3>Feed inventory</h3>
            <DataTable caption="Feed inventory" columns={[
              { key: "feedTypeName", label: "Feed" },
              { key: "brand", label: "Brand", render: item => item.brand || "—" },
              { key: "packagingName", label: "Package", render: item => item.packagingName || "—" },
              { key: "purchasedQuantityMilli", label: "Purchased", render: item => `${feedQuantity(item.purchasedQuantityMilli)} ${item.unit}` },
              { key: "openingQuantityMilli", label: "Opening stock", render: item => `${feedQuantity(item.openingQuantityMilli)} ${item.unit}` },
              { key: "consumedQuantityMilli", label: "Used", render: item => `${feedQuantity(item.consumedQuantityMilli)} ${item.unit}` },
              { key: "remainingQuantityMilli", label: "Remaining", render: item => `${feedQuantity(item.remainingQuantityMilli)} ${item.unit}` },
              { key: "remainingCostMinor", label: "Remaining cost", render: item => item.remainingCostMinor == null ? "Unknown" : formatMoney(item.remainingCostMinor, farm.currency) },
            ]} rows={feedInventory} emptyMessage="No feed inventory yet. Add a feed type and record a purchase or opening balance." />
          </div>
        </section></PageSection>

      <PageSection page="feed-stock" tab="inventory"><section id="inventory" className="card full">
          <div className="section-head">
            <h2>Inventory</h2>
            <p className="muted">Track drugs, vaccines, and farm supplies with explicit stock movements. Feed remains in the dedicated Feed module because its FIFO cost ledger is authoritative.</p>
          </div>

          <InventoryAnalytics items={inventoryItems} movements={inventoryMovements} selectedItemId={inventoryMovementForm.itemId || drawerMovementItemId} dateRange={dateRange} setDateRange={setDateRange} timezone={farm?.timezone} dateFormat={applicationSettings?.dateFormat} />

          <div className="subsection">
            <h3>Inventory items</h3>
            <ModalForm title="Inventory item" description="Create or update a drug, vaccine, or farm-supply item." triggerLabel={editingInventoryItemId ? "Edit inventory item" : "Add inventory item"} openSignal={inventoryItemOpenSignal} loading={saving} error={error} onDiscard={() => { setInventoryItemForm(emptyInventoryItem); cancelInventoryItemEdit(); }}>
<ValidatedForm className="inline-form" onSubmit={saveInventoryItem}>
              <label className="field"><span>Item name</span><input required value={inventoryItemForm.name} onChange={e=>setInventoryItemForm({...inventoryItemForm,name:e.target.value})}/></label>
              <label className="field"><span>Category</span><select value={inventoryItemForm.category} onChange={e=>setInventoryItemForm({...inventoryItemForm,category:e.target.value})}>
                <option value="SUPPLY">Farm supply</option>
                <option value="DRUG">Drug</option>
                <option value="VACCINE">Vaccine</option>
              </select></label>
              <label className="field"><span>Unit e.g. bottle, piece, dose</span><input required value={inventoryItemForm.unit} onChange={e=>setInventoryItemForm({...inventoryItemForm,unit:e.target.value})}/></label>
              <OptionalFields title="Stock alert settings"><label className="field"><span>Reorder level (optional)</span><input min="0" step="0.001" type="number" value={inventoryItemForm.reorderLevel} onChange={e=>setInventoryItemForm({...inventoryItemForm,reorderLevel:e.target.value})}/></label></OptionalFields>
              <button disabled={saving}>{editingInventoryItemId ? "Save item" : "Add item"}</button>{editingInventoryItemId && <button type="button" className="secondary-button" onClick={cancelInventoryItemEdit}>Cancel</button>}
            </ValidatedForm>
</ModalForm>
            <DataTable caption="Inventory items" columns={[
              { key: "name", label: "Item" }, { key: "category", label: "Category" }, { key: "quantityOnHand", label: "Stock" },
              { key: "details", label: "Details", render: item => <button type="button" className="secondary-button" onClick={() => openInventoryDetails(item)}>View</button> },
              { key: "status", label: "Status" },
            ]} rows={inventoryItems} emptyMessage="No non-feed inventory items yet." />
          </div>

          <div className="subsection">
            <h3>Feed stock</h3>
            <p className="muted">Feed is shown here from the dedicated FIFO feed ledger. Do not create a second generic item for feed.</p>
            <DataTable caption="Feed stock summary" columns={[
              { key: "feedTypeName", label: "Feed" },
              { key: "remainingQuantityMilli", label: "Remaining", render: item => `${feedQuantity(item.remainingQuantityMilli)} ${item.unit}` },
              { key: "consumedQuantityMilli", label: "Used", render: item => `${feedQuantity(item.consumedQuantityMilli)} ${item.unit}` },
              { key: "remainingCostMinor", label: "Remaining cost", render: item => item.remainingCostMinor == null ? "Unknown" : formatMoney(item.remainingCostMinor, farm.currency) },
            ]} rows={feedInventory} emptyMessage="No feed stock yet. Record a purchase or an opening balance for an existing feed type." />
          </div>

          <div className="subsection">
            <h3>Record inventory movement</h3>
            <ModalForm title="Inventory movement" description="Record a stock receipt, issue, adjustment, or waste movement." triggerLabel="Record movement" loading={saving} error={error} onDiscard={() => setInventoryMovementForm(emptyInventoryMovement)}>
<ValidatedForm className="inline-form" onSubmit={saveInventoryMovement}>
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
              <OptionalFields title="Batch allocation" description="Leave this closed for farm-level stock movements."><label className="field"><span>Batch association (optional)</span><select value={inventoryMovementForm.batchId} onChange={e=>setInventoryMovementForm({...inventoryMovementForm,batchId:e.target.value})}>
                <option value="">Farm-level</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select></label></OptionalFields>
              <button disabled={saving || !inventoryMovementForm.itemId}>Record movement</button>
            </ValidatedForm>
</ModalForm>
            {inventoryMovementForm.itemId && <DataTable caption="Inventory movement history" columns={[
              { key: "movementDate", label: "Date" }, { key: "movementType", label: "Movement" },
              { key: "quantity", label: "Quantity" }, { key: "reason", label: "Reason" }, { key: "source", label: "Source" },
              { key: "batchId", label: "Batch", render: row => row.batchId ? (batches.find(batch => batch.id === row.batchId)?.code || row.batchId) : "Farm" },
            ]} rows={inventoryMovements} emptyMessage="No stock movements recorded for this item yet." notApplicable={!inventoryMovementForm.itemId} />}
          </div>
        </section></PageSection>
      <PageSection page="feed-stock" tab="purchases"><section id="purchases" className="card full"><div className="section-head"><h2>Feed purchases</h2><p className="muted">Choose a registered supplier or leave it blank if the source is unknown.</p></div>
          <div className="subsection">
            <h3>Purchase feed</h3>
            <ModalForm title="Feed purchase" description="Record a feed purchase. The backend updates the FIFO stock ledger and expense record." triggerLabel="Record purchase" openSignal={feedPurchaseModalSignal} loading={saving} error={error} onDiscard={() => setFeedPurchaseForm(emptyFeedPurchase)}>
<MultiStepForm
              className="form-grid"
              loading={saving}
              submitLabel="Record purchase"
              onSubmit={purchaseFeed}
              steps={[
                {
                  title: "Feed details",
                  description: "Select the registered feed product and the date the purchase was made.",
                  content: <>
                    <label className="field"><span>Feed type</span><select name="feedTypeId" required value={feedPurchaseForm.feedTypeId} onChange={e=>{
                      const type=feedTypes.find(item=>item.id===e.target.value);
                      setFeedPurchaseForm({...feedPurchaseForm,feedTypeId:e.target.value,unit:type?.unit||"bag"});
                    }}>
                      <option value="">Feed type</option>{feedTypes.filter(t=>t.status==="ACTIVE").map(t=><option key={t.id} value={t.id}>{feedProductLabel(t)}</option>)}
                    </select></label>
                    <label className="field"><span>Purchase date</span><input name="purchaseDate" required type="date" value={feedPurchaseForm.purchaseDate} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,purchaseDate:e.target.value})}/></label>
                  </>,
                },
                {
                  title: "Quantity and pricing",
                  description: "Enter the quantity in the selected feed product's configured unit and the full purchase cost.",
                  content: <>
                    <label className="field"><span>Quantity</span><input name="quantity" required min="0.001" step="0.001" type="number" value={feedPurchaseForm.quantity} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,quantity:e.target.value})}/></label>
                    <label className="field"><span>Total cost (₦)</span><input name="totalCostMinor" required type="text" inputMode="decimal" value={feedPurchaseForm.totalCostMinor} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,totalCostMinor:e.target.value})}/></label>
                  </>,
                },
                {
                  title: "Supplier information",
                  description: "Supplier details are optional. You can continue when the source is unknown.",
                  content: <OptionalFields title="Purchase source" description="Choose a registered supplier if one was involved.">
                    <label className="field"><span>Supplier (optional)</span><select value={feedPurchaseForm.supplierId} onChange={e=>setFeedPurchaseForm({...feedPurchaseForm,supplierId:e.target.value})}><option value="">No supplier recorded</option>{suppliers.filter(supplier=>supplier.status!=="ARCHIVED").map(supplier=><option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label>
                  </OptionalFields>,
                },
                {
                  title: "Review and save",
                  description: "Confirm the purchase details. Saving creates the purchase, FIFO stock lot and its corresponding expense through the existing backend workflow.",
                  content: <dl className="workflow-review">
                    <div><dt>Feed type</dt><dd>{feedProductLabel(feedTypes.find(type => type.id === feedPurchaseForm.feedTypeId) || { name: "Not selected", unit: feedPurchaseForm.unit })}</dd></div>
                    <div><dt>Purchase date</dt><dd>{feedPurchaseForm.purchaseDate || "Not entered"}</dd></div>
                    <div><dt>Quantity</dt><dd>{feedPurchaseForm.quantity || "Not entered"} {feedTypes.find(type => type.id === feedPurchaseForm.feedTypeId)?.unit || feedPurchaseForm.unit}</dd></div>
                    <div><dt>Total cost</dt><dd>{feedPurchaseForm.totalCostMinor ? `₦${feedPurchaseForm.totalCostMinor}` : "Not entered"}</dd></div>
                    <div><dt>Supplier</dt><dd>{suppliers.find(supplier => supplier.id === feedPurchaseForm.supplierId)?.name || "Not recorded"}</dd></div>
                  </dl>,
                },
              ]}
            />
</ModalForm>
          </div>

      </section></PageSection>

      <PageSection page="feed-stock" tab="suppliers"><section id="suppliers" className="card full">
        <div className="section-head"><h2>Suppliers</h2><p className="muted">Maintain reusable supplier records for feed purchases. Archiving keeps historical purchase references intact.</p></div>
        <ModalForm title="Supplier" description="Add or update a reusable supplier record." triggerLabel={editingSupplierId ? "Edit supplier" : "Add supplier"} openSignal={supplierOpenSignal} loading={saving} error={error} onDiscard={() => { setSupplierForm(emptySupplier); cancelSupplierEdit(); }}>
<ValidatedForm className="form-grid" onSubmit={saveSupplier} validate={() => {
          const duplicate = suppliers.some(supplier => supplier.id !== editingSupplierId && supplier.name.trim() === supplierForm.name.trim());
          return duplicate ? { supplier_name: "A supplier with this name already exists." } : {};
        }}>
          <label>Supplier name<input required maxLength="120" value={supplierForm.name} onChange={event=>setSupplierForm({...supplierForm,name:event.target.value})}/></label>
          <label>Phone<input maxLength="40" value={supplierForm.phone} onChange={event=>setSupplierForm({...supplierForm,phone:event.target.value})}/></label>
          <label>Email<input type="email" maxLength="160" value={supplierForm.email} onChange={event=>setSupplierForm({...supplierForm,email:event.target.value})}/></label>
          <label>Address<input maxLength="240" value={supplierForm.address} onChange={event=>setSupplierForm({...supplierForm,address:event.target.value})}/></label>
          <label className="full">Notes<input value={supplierForm.notes} onChange={event=>setSupplierForm({...supplierForm,notes:event.target.value})}/></label>
          <button disabled={saving}>{editingSupplierId ? "Save supplier" : "Add supplier"}</button>{editingSupplierId && <button type="button" className="secondary-button" onClick={cancelSupplierEdit}>Cancel</button>}
        </ValidatedForm>
</ModalForm>
        <DataTable caption="Suppliers" columns={[
          { key: "name", label: "Supplier" },
          { key: "linkedBatches", label: "Linked batches", render: supplier => batches.filter(batch => batch.supplierId === supplier.id).length },
          { key: "details", label: "Details", render: supplier => <button type="button" className="secondary-button" onClick={() => openSupplierDetails(supplier)}>View</button> },
          { key: "status", label: "Status", render: supplier => supplier.status || "ACTIVE" },
          { key: "action", label: "Action", render: supplier => supplier.status === "ARCHIVED" ? "Archived" : <ConfirmAction title="Archive supplier?" description={`Archive ${supplier.name}? It will no longer be offered for new purchases, while historical purchase references remain intact.`} triggerLabel="Archive" confirmLabel="Archive supplier" loading={saving} error={error} onConfirm={() => archiveSupplier(supplier.id)} /> },
        ]} rows={suppliers} emptyMessage="No suppliers recorded yet." />
      </section></PageSection>

      <ContextDrawer
        open={Boolean(drawerRecord)}
        onClose={() => {
          drawerMovementRequestRef.current += 1;
          setDrawerRecord(null);
        }}
        title={drawerRecord?.title || "Record details"}
        description={drawerRecord?.description}
        size={drawerRecord?.kind === "supplier" || drawerRecord?.kind === "inventory" ? "large" : "medium"}
        footer={drawerRecord?.kind === "inventory" ? <button type="button" onClick={() => {
          editInventoryItem(drawerRecord.item);
          setDrawerRecord(null);
          setInventoryItemOpenSignal(value => value + 1);
        }}>Edit inventory item</button> : drawerRecord?.kind === "supplier" ? <button type="button" onClick={() => {
          editSupplier(drawerRecord.supplier);
          setDrawerRecord(null);
          setSupplierOpenSignal(value => value + 1);
        }}>Edit supplier</button> : null}
      >
        {drawerRecord && <DrawerDetails fields={drawerRecord.fields} />}
        {drawerRecord?.kind === "inventory" && <section className="subsection" aria-labelledby="inventory-movement-history-title">
          <h3 id="inventory-movement-history-title">Stock movement history</h3>
          {drawerMovementItemId !== drawerRecord.item.id ? <p className="muted">Loading stock movements…</p> : inventoryMovements.length === 0
            ? <p className="muted">No stock movements recorded for this item.</p>
            : <ol className="pomas-drawer-record-list">
              {inventoryMovements.map((movement, index) => {
                const batch = batches.find(item => item.id === movement.batchId);
                return <li key={movement.id || `${movement.movementDate}-${index}`}>
                  <div><strong>{movement.movementDate || "Undated"} · {movement.movementType || "Movement"}</strong><span>{movement.reason || "No reason recorded"} · {movement.source || "Source not recorded"}</span><span>{batch?.code || (movement.batchId ? movement.batchId : "Farm-level")}</span></div>
                  <strong>{movement.quantity ?? "—"} {drawerRecord.item.unit || ""}</strong>
                </li>;
              })}
            </ol>}
        </section>}
        {drawerRecord?.kind === "supplier" && <section className="subsection" aria-labelledby="supplier-batch-history-title">
          <h3 id="supplier-batch-history-title">Linked batch records</h3>
          {drawerRecord.relatedBatches.length === 0 ? <p className="muted">No batch currently references this supplier as its source. Feed purchase history is not exposed in this view.</p> : <ol className="pomas-drawer-record-list">
            {drawerRecord.relatedBatches.map(batch => <li key={batch.id}>
              <div><strong>{batch.code}</strong><span>{batch.type} · {batch.placementDate || "Placement date unavailable"}</span></div>
              <strong>{batch.status || "—"}</strong>
            </li>)}
          </ol>}
        </section>}
      </ContextDrawer>
    </PageTemplate>
  );
}
