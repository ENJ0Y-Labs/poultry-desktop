import { useEffect, useState } from "react";
const emptyWeight = {
  batchId: "",
  date: new Date().toISOString().slice(0, 10),
  sampleQuantity: "",
  totalWeightKg: "",
  notes: "",
};
const emptyBirdSale = {
  batchId: "",
  date: new Date().toISOString().slice(0, 10),
  quantity: "",
  pricePerBirdMinor: "",
  customer: "",
  customerId: "",
};
const emptyPricing = { quantity: "", date: new Date().toISOString().slice(0, 10) };
import { batchApi, broilerApi, costApi, dailyApi, eggApi, expenseApi, farmApi, feedApi, populationApi, healthApi, pricingApi, customerApi, salesApi, inventoryApi } from "./services/api.js";

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
const emptyFeedType = { name: "", unit: "bag", applicableType: "BOTH" };
const emptyFeedPurchase = {
  feedTypeId: "",
  supplierId: "",
  purchaseDate: new Date().toISOString().slice(0, 10),
  quantity: "",
  unit: "bag",
  totalCostMinor: "",
};
const emptyFeedUsage = {
  batchId: "",
  feedTypeId: "",
  usageDate: new Date().toISOString().slice(0, 10),
  quantity: "",
  unit: "bag",
  reason: "",
};
const emptyDaily = {
  batchId: "",
  date: new Date().toISOString().slice(0, 10),
  notes: "",
  water: "25:4, 75:2",
};
const emptyPopulationEvent = {
  batchId: "",
  date: new Date().toISOString().slice(0, 10),
  quantity: "",
  reason: "",
  notes: "",
};
const emptyHealth = {
  batchId: "",
  date: new Date().toISOString().slice(0, 10),
  conditionProblem: "",
  description: "",
  action: "",
};
const emptyDrug = {
  batchId: "",
  date: new Date().toISOString().slice(0, 10),
  drug: "",
  quantity: "",
  costMinor: "",
  reason: "",
};
const emptyVaccination = {
  batchId: "",
  date: new Date().toISOString().slice(0, 10),
  vaccine: "",
  dose: "",
  quantity: "",
  notes: "",
};
const emptyExpense = {
  batchId: "",
  occurredDate: new Date().toISOString().slice(0, 10),
  description: "",
  amountMinor: "",
  category: "OTHER",
};
const emptyCustomer = { name: "", phone: "", notes: "" };
const emptyInventoryItem = { name: "", category: "SUPPLY", unit: "piece", reorderLevel: "" };
const emptyInventoryMovement = {
  itemId: "", date: new Date().toISOString().slice(0, 10), movementType: "RECEIVE",
  quantity: "", reason: "", source: "", batchId: ""
};

export default function App() {
  const [farm, setFarm] = useState(null);
  const [farmForm, setFarmForm] = useState(emptyFarm);
  const [houses, setHouses] = useState([]);
  const [batches, setBatches] = useState([]);
  const [batchForm, setBatchForm] = useState(emptyBatch);
  const [costForm, setCostForm] = useState(emptyCost);
  const [feedTypes, setFeedTypes] = useState([]);
  const [feedInventory, setFeedInventory] = useState([]);
  const [feedTypeForm, setFeedTypeForm] = useState(emptyFeedType);
  const [feedPurchaseForm, setFeedPurchaseForm] = useState(emptyFeedPurchase);
  const [feedUsageForm, setFeedUsageForm] = useState(emptyFeedUsage);
  const [dailyForm, setDailyForm] = useState(emptyDaily);
  const [dailyRecord, setDailyRecord] = useState(null);
  const [mortalityForm, setMortalityForm] = useState(emptyPopulationEvent);
  const [cullingForm, setCullingForm] = useState(emptyPopulationEvent);
  const [healthForm, setHealthForm] = useState(emptyHealth);
  const [drugForm, setDrugForm] = useState(emptyDrug);
  const [vaccinationForm, setVaccinationForm] = useState(emptyVaccination);
  const [healthRecords, setHealthRecords] = useState([]);
  const [drugRecords, setDrugRecords] = useState([]);
  const [vaccinationRecords, setVaccinationRecords] = useState([]);
  const [expenseForm, setExpenseForm] = useState(emptyExpense);
  const [expenses, setExpenses] = useState([]);
  const [expenseBatchFilter, setExpenseBatchFilter] = useState("");
  const [inventoryItems, setInventoryItems] = useState([]);
  const [inventoryItemForm, setInventoryItemForm] = useState(emptyInventoryItem);
  const [inventoryMovementForm, setInventoryMovementForm] = useState(emptyInventoryMovement);
  const [inventoryMovements, setInventoryMovements] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [customerForm, setCustomerForm] = useState(emptyCustomer);
  const [sales, setSales] = useState([]);
  const [eggBatchId, setEggBatchId] = useState("");
  const [broilerBatchId, setBroilerBatchId] = useState("");
  const [broilerGrowth, setBroilerGrowth] = useState(null);
  const [broilerWeights, setBroilerWeights] = useState([]);
  const [broilerSales, setBroilerSales] = useState([]);
  const [weightForm, setWeightForm] = useState(emptyWeight);
  const [birdSaleForm, setBirdSaleForm] = useState(emptyBirdSale);
  const [pricingSettings, setPricingSettings] = useState({ targetMarginPercent: null, workingMarginPercent: null });
  const [pricing, setPricing] = useState(null);
  const [pricingForm, setPricingForm] = useState(emptyPricing);
  const [eggInventory, setEggInventory] = useState(null);
  const [eggCollections, setEggCollections] = useState([]);
  const [eggSales, setEggSales] = useState([]);
  const [eggCollectionForm, setEggCollectionForm] = useState({
    batchId: "", date: new Date().toISOString().slice(0, 10), good: "", cracked: "", notes: ""
  });
  const [eggSaleForm, setEggSaleForm] = useState({
    batchId: "", date: new Date().toISOString().slice(0, 10), customer: "", customerId: "", crates: "", pricePerCrateMinor: ""
  });
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
      const currentBatches = await batchApi.list();
      setBatches(currentBatches);
      setExpenses(await expenseApi.list());
      setCustomers(await customerApi.list());
      setSales(await salesApi.list());
      setInventoryItems(await inventoryApi.listItems());
      setFeedTypes(await feedApi.listTypes());
      setFeedInventory(await feedApi.inventory());
      setPricingSettings(await pricingApi.settings());
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
      setExpenses([]);
      setCustomers([]);
      setSales([]);
      setInventoryItems([]);
      setInventoryMovements([]);
      setFeedTypes([]);
      setFeedInventory([]);
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

  async function loadExpenses(batchId = expenseBatchFilter) {
    try {
      setExpenses(await expenseApi.list(batchId || null));
    } catch (err) { setError(err.message); }
  }
  async function loadSales() {
    try { setSales(await salesApi.list()); }
    catch (err) { setError(err.message); }
  }

  async function saveCustomer(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await customerApi.create({
        name: customerForm.name.trim(),
        phone: customerForm.phone.trim() || null,
        notes: customerForm.notes.trim() || null,
      });
      setCustomers(current => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setCustomerForm(emptyCustomer);
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }


  async function saveInventoryItem(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await inventoryApi.createItem({
        name: inventoryItemForm.name.trim(),
        category: inventoryItemForm.category,
        unit: inventoryItemForm.unit.trim(),
        reorderLevel: inventoryItemForm.reorderLevel === "" ? 0 : Number(inventoryItemForm.reorderLevel),
      });
      setInventoryItems(current => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setInventoryItemForm(emptyInventoryItem);
      setInventoryMovementForm(form => ({ ...form, itemId: created.id }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function loadInventoryMovements(itemId = inventoryMovementForm.itemId) {
    if (!itemId) { setInventoryMovements([]); return; }
    try { setInventoryMovements(await inventoryApi.listMovements(itemId)); }
    catch (err) { setError(err.message); }
  }

  async function saveInventoryMovement(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await inventoryApi.recordMovement(inventoryMovementForm.itemId, {
        movementDate: inventoryMovementForm.date,
        movementType: inventoryMovementForm.movementType,
        quantity: Number(inventoryMovementForm.quantity),
        reason: inventoryMovementForm.reason.trim(),
        source: inventoryMovementForm.source.trim(),
        batchId: inventoryMovementForm.batchId || null,
      });
      setInventoryItems(await inventoryApi.listItems());
      setInventoryMovementForm(form => ({ ...emptyInventoryMovement, itemId: form.itemId }));
      await loadInventoryMovements(inventoryMovementForm.itemId);
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveExpense(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await expenseApi.create({
        occurredDate: expenseForm.occurredDate,
        description: expenseForm.description.trim(),
        amountMinor: Number(expenseForm.amountMinor),
        category: expenseForm.category,
        batchId: expenseForm.batchId || null,
      });
      setExpenses(current => [created, ...current]);
      setExpenseForm(form => ({ ...emptyExpense, batchId: form.batchId }));
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
        purchaseCostMinor: batchForm.purchaseCostMinor,
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
        amountMinor: costForm.amountMinor,
        reason: costForm.reason.trim() || null,
      });
      setCostForm(form => ({ ...emptyCost, batchId: form.batchId }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function createFeedType(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await feedApi.createType(feedTypeForm);
      setFeedTypes(current => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setFeedTypeForm(emptyFeedType);
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function purchaseFeed(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const type = feedTypes.find(item => item.id === feedPurchaseForm.feedTypeId);
      await feedApi.purchase({
        feedTypeId: feedPurchaseForm.feedTypeId,
        supplierId: feedPurchaseForm.supplierId.trim() || null,
        purchaseDate: feedPurchaseForm.purchaseDate,
        quantity: feedPurchaseForm.quantity,
        unit: type?.unit || feedPurchaseForm.unit,
        totalCostMinor: feedPurchaseForm.totalCostMinor,
      });
      setFeedPurchaseForm(form => ({ ...emptyFeedPurchase, feedTypeId: form.feedTypeId }));
      setFeedInventory(await feedApi.inventory());
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function useFeed(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const type = feedTypes.find(item => item.id === feedUsageForm.feedTypeId);
      await feedApi.use(feedUsageForm.batchId, {
        feedTypeId: feedUsageForm.feedTypeId,
        usageDate: feedUsageForm.usageDate,
        quantity: feedUsageForm.quantity,
        unit: type?.unit || feedUsageForm.unit,
        reason: feedUsageForm.reason.trim() || "Production feed usage",
      });
      setFeedUsageForm(form => ({ ...emptyFeedUsage, batchId: form.batchId, feedTypeId: form.feedTypeId }));
      setFeedInventory(await feedApi.inventory());
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  function feedQuantity(value) {
    return (Number(value || 0) / 1000).toString();
  }

  function parseWaterEntries(value) {
    return value.split(",").map(item => item.trim()).filter(Boolean).map(item => {
      const [size, count] = item.split(":").map(part => Number(part.trim()));
      if (!Number.isInteger(size) || size <= 0 || !Number.isInteger(count) || count <= 0) {
        throw new Error("Water format must be like 25:4, 75:2.");
      }
      return { containerSizeUnits: size, containerCount: count };
    });
  }

  async function saveDailyRecord(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await dailyApi.create(dailyForm.batchId, {
        recordDate: dailyForm.date,
        notes: dailyForm.notes.trim() || null,
        waterContainers: parseWaterEntries(dailyForm.water),
      });
      setDailyRecord(created);
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function recordPopulationEvent(event, type) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const form = type === "MORTALITY" ? mortalityForm : cullingForm;
      const save = type === "MORTALITY" ? populationApi.mortality : populationApi.culling;
      await save(form.batchId, {
        eventDate: form.date,
        quantity: Number(form.quantity),
        reason: form.reason.trim() || null,
        notes: form.notes.trim() || null,
      });
      const record = await dailyApi.get(form.batchId, form.date);
      setDailyRecord(record);
      if (type === "MORTALITY") setMortalityForm({ ...emptyPopulationEvent, batchId: form.batchId });
      else setCullingForm({ ...emptyPopulationEvent, batchId: form.batchId });
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }


  async function loadPricingForBatch(batchId, quantity, date) {
    if (!batchId || !quantity) { setPricing(null); return; }
    try { setPricing(await pricingApi.price(batchId, Number(quantity), date || undefined)); }
    catch (err) { setError(err.message); }
  }

  async function savePricingSettings(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const updated = await pricingApi.updateSettings({
        targetMarginPercent: pricingSettings.targetMarginPercent === "" ? null : pricingSettings.targetMarginPercent
      });
      setPricingSettings(updated);
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function loadBroilerForBatch(batchId) {
    if (!batchId) {
      setBroilerGrowth(null); setBroilerWeights([]); setBroilerSales([]);
      return;
    }
    try {
      const [growth, weights, sales] = await Promise.all([
        broilerApi.growth(batchId),
        broilerApi.weights(batchId),
        broilerApi.sales(batchId),
      ]);
      setBroilerGrowth(growth);
      setBroilerWeights(weights);
      setBroilerSales(sales);
    } catch (err) { setError(err.message); }
  }

  async function saveWeight(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const batchId = weightForm.batchId;
      await broilerApi.addWeight(batchId, {
        recordDate: weightForm.date,
        sampleQuantity: Number(weightForm.sampleQuantity),
        totalWeightKg: weightForm.totalWeightKg,
        notes: weightForm.notes.trim() || null,
      });
      await loadBroilerForBatch(batchId);
      setWeightForm(form => ({ ...form, sampleQuantity: "", totalWeightKg: "", notes: "" }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveBirdSale(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const batchId = birdSaleForm.batchId;
      const sale = {
        recordDate: birdSaleForm.date,
        quantity: Number(birdSaleForm.quantity),
        pricePerBirdMinor: birdSaleForm.pricePerBirdMinor,
        customer: birdSaleForm.customer.trim(),
        customerId: birdSaleForm.customerId || null,
      };
      try {
        await broilerApi.addSale(batchId, sale);
      } catch (err) {
        if (!err.message.includes("below the configured target margin")) throw err;
        if (!window.confirm("This sale is below the configured target margin. Record it anyway? The target margin will not be lowered.")) throw err;
        await broilerApi.addSale(batchId, { ...sale, confirmedBelowTarget: true });
      }
      await loadBroilerForBatch(batchId);
      setBatches(await batchApi.list());
      setBirdSaleForm(form => ({ ...form, quantity: "", pricePerBirdMinor: "", customer: "", customerId: "" }));
      await loadSales();
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function loadEggsForBatch(batchId) {
    if (!batchId) {
      setEggInventory(null); setEggCollections([]); setEggSales([]);
      return;
    }
    try {
      const [inventory, collections, sales] = await Promise.all([
        eggApi.inventory(batchId),
        eggApi.listCollections(batchId),
        eggApi.listSales(batchId),
      ]);
      setEggInventory(inventory);
      setEggCollections(collections);
      setEggSales(sales);
    } catch (err) { setError(err.message); }
  }

  async function saveEggCollection(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const batchId = eggCollectionForm.batchId;
      await eggApi.addCollection(batchId, {
        recordDate: eggCollectionForm.date,
        good: Number(eggCollectionForm.good),
        cracked: Number(eggCollectionForm.cracked),
        notes: eggCollectionForm.notes.trim() || null,
      });
      await loadEggsForBatch(batchId);
      setEggCollectionForm(form => ({ ...form, good: "", cracked: "", notes: "" }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveEggSale(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const batchId = eggSaleForm.batchId;
      await eggApi.addSale(batchId, {
        recordDate: eggSaleForm.date,
        customer: eggSaleForm.customer.trim(),
        customerId: eggSaleForm.customerId || null,
        crates: eggSaleForm.crates,
        pricePerCrateMinor: eggSaleForm.pricePerCrateMinor,
      });
      await loadEggsForBatch(batchId);
      setEggSaleForm(form => ({ ...form, customer: "", customerId: "", crates: "", pricePerCrateMinor: "" }));
      await loadSales();
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveHealth(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await healthApi.add(healthForm.batchId, {
        recordDate: healthForm.date,
        conditionProblem: healthForm.conditionProblem.trim(),
        description: healthForm.description.trim(),
        action: healthForm.action.trim(),
      });
      setHealthRecords(await healthApi.listHealth(healthForm.batchId));
      setHealthForm(form => ({ ...emptyHealth, batchId: form.batchId }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveDrug(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await healthApi.addDrug(drugForm.batchId, {
        recordDate: drugForm.date,
        drug: drugForm.drug.trim(),
        quantity: Number(drugForm.quantity),
        costMinor: drugForm.costMinor,
        reason: drugForm.reason.trim(),
      });
      setDrugRecords(await healthApi.listDrugs(drugForm.batchId));
      setDrugForm(form => ({ ...emptyDrug, batchId: form.batchId }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function saveVaccination(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await healthApi.addVaccination(vaccinationForm.batchId, {
        recordDate: vaccinationForm.date,
        vaccine: vaccinationForm.vaccine.trim(),
        dose: vaccinationForm.dose.trim(),
        quantity: Number(vaccinationForm.quantity),
        notes: vaccinationForm.notes.trim() || null,
      });
      setVaccinationRecords(await healthApi.listVaccinations(vaccinationForm.batchId));
      setVaccinationForm(form => ({ ...emptyVaccination, batchId: form.batchId }));
    } catch (err) { setError(err.message); } finally { setSaving(false); }
  }

  async function loadHealthForBatch(batchId) {
    if (!batchId) return;
    try {
      const [health, drugs, vaccinations] = await Promise.all([
        healthApi.listHealth(batchId),
        healthApi.listDrugs(batchId),
        healthApi.listVaccinations(batchId),
      ]);
      setHealthRecords(health);
      setDrugRecords(drugs);
      setVaccinationRecords(vaccinations);
    } catch (err) { setError(err.message); }
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

        <section className="card"><h2>Pricing & margins</h2><form className="form-grid" onSubmit={savePricingSettings}>
          <label>Target margin %<input type="number" min="0" max="99.999999" step="0.01" placeholder="e.g. 25" value={pricingSettings.targetMarginPercent ?? ""} onChange={e=>setPricingSettings({...pricingSettings,targetMarginPercent:e.target.value})}/><small>Leave empty for no target. A sale never silently lowers it.</small></label>
          <label>Working/latest margin %<input readOnly value={pricingSettings.workingMarginPercent ?? ""}/><small>Updated from actual sale results.</small></label>
          <button disabled={saving}>Save pricing target</button>
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

        <section className="card full">
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
                <span>{item.feedTypeName}</span><span>{feedQuantity(item.purchasedQuantityMilli)} {item.unit}</span><span>{feedQuantity(item.consumedQuantityMilli)} {item.unit}</span><span>{feedQuantity(item.remainingQuantityMilli)} {item.unit}</span><span>{item.remainingCostMinor}</span>
              </div>)}
              {feedInventory.length===0 && <p className="muted empty">No feed inventory yet.</p>}
            </div>
          </div>
        </section>

        <section className="card full">
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
        </section>

        <section className="card full">
          <div className="section-head">
            <h2>Health management</h2>
            <p className="muted">Keep health observations, treatments, and vaccinations as separate operational records. Drug costs enter the shared Drugs expense category.</p>
          </div>

          <div className="subsection">
            <h3>Health record</h3>
            <form className="inline-form" onSubmit={saveHealth}>
              <select required value={healthForm.batchId} onChange={e=>{setHealthForm({...healthForm,batchId:e.target.value});loadHealthForBatch(e.target.value);}}>
                <option value="">Batch</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
              <input required type="date" value={healthForm.date} onChange={e=>setHealthForm({...healthForm,date:e.target.value})}/>
              <input required placeholder="Condition / problem" value={healthForm.conditionProblem} onChange={e=>setHealthForm({...healthForm,conditionProblem:e.target.value})}/>
              <input required placeholder="Description" value={healthForm.description} onChange={e=>setHealthForm({...healthForm,description:e.target.value})}/>
              <input required placeholder="Action taken" value={healthForm.action} onChange={e=>setHealthForm({...healthForm,action:e.target.value})}/>
              <button disabled={saving}>Record health issue</button>
            </form>
            <div className="table">
              <div className="row header"><span>Date</span><span>Condition</span><span>Description</span><span>Action</span></div>
              {healthRecords.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.conditionProblem}</span><span>{row.description}</span><span>{row.action}</span></div>)}
              {healthRecords.length===0 && <p className="muted empty">Select a batch to view health records.</p>}
            </div>
          </div>

          <div className="subsection">
            <h3>Drugs</h3>
            <form className="inline-form" onSubmit={saveDrug}>
              <select required value={drugForm.batchId} onChange={e=>{setDrugForm({...drugForm,batchId:e.target.value});loadHealthForBatch(e.target.value);}}>
                <option value="">Batch</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
              <input required type="date" value={drugForm.date} onChange={e=>setDrugForm({...drugForm,date:e.target.value})}/>
              <input required placeholder="Drug" value={drugForm.drug} onChange={e=>setDrugForm({...drugForm,drug:e.target.value})}/>
              <input required min="1" type="number" placeholder="Quantity" value={drugForm.quantity} onChange={e=>setDrugForm({...drugForm,quantity:e.target.value})}/>
              <input required min="1" step="1" type="number" placeholder="Cost (minor units)" value={drugForm.costMinor} onChange={e=>setDrugForm({...drugForm,costMinor:e.target.value})}/>
              <input required placeholder="Reason" value={drugForm.reason} onChange={e=>setDrugForm({...drugForm,reason:e.target.value})}/>
              <button disabled={saving}>Record drug</button>
            </form>
            <div className="table">
              <div className="row header"><span>Date</span><span>Drug</span><span>Quantity</span><span>Cost</span><span>Reason</span></div>
              {drugRecords.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.drug}</span><span>{row.quantity}</span><span>{row.costMinor}</span><span>{row.reason}</span></div>)}
              {drugRecords.length===0 && <p className="muted empty">No drug records for the selected batch.</p>}
            </div>
          </div>

          <div className="subsection">
            <h3>Vaccination</h3>
            <form className="inline-form" onSubmit={saveVaccination}>
              <select required value={vaccinationForm.batchId} onChange={e=>{setVaccinationForm({...vaccinationForm,batchId:e.target.value});loadHealthForBatch(e.target.value);}}>
                <option value="">Batch</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
              <input required type="date" value={vaccinationForm.date} onChange={e=>setVaccinationForm({...vaccinationForm,date:e.target.value})}/>
              <input required placeholder="Vaccine" value={vaccinationForm.vaccine} onChange={e=>setVaccinationForm({...vaccinationForm,vaccine:e.target.value})}/>
              <input required placeholder="Dose e.g. 1 dose" value={vaccinationForm.dose} onChange={e=>setVaccinationForm({...vaccinationForm,dose:e.target.value})}/>
              <input required min="1" type="number" placeholder="Quantity" value={vaccinationForm.quantity} onChange={e=>setVaccinationForm({...vaccinationForm,quantity:e.target.value})}/>
              <input placeholder="Notes" value={vaccinationForm.notes} onChange={e=>setVaccinationForm({...vaccinationForm,notes:e.target.value})}/>
              <button disabled={saving}>Record vaccination</button>
            </form>
            <div className="table">
              <div className="row header"><span>Date</span><span>Vaccine</span><span>Dose</span><span>Quantity</span><span>Notes</span></div>
              {vaccinationRecords.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.vaccine}</span><span>{row.dose}</span><span>{row.quantity}</span><span>{row.notes||"—"}</span></div>)}
              {vaccinationRecords.length===0 && <p className="muted empty">No vaccination records for the selected batch.</p>}
            </div>
          </div>
        </section>

        <section className="card full">
          <div className="section-head">
            <h2>Layer egg production</h2>
            <p className="muted">Only LAYER batches can record eggs. Good and cracked eggs are stored as individual eggs. Cracked eggs remain historical and are never sellable.</p>
          </div>

          <div className="subsection">
            <h3>Egg batch</h3>
            <select value={eggBatchId} onChange={e => {
              const id = e.target.value;
              setEggBatchId(id);
              setEggCollectionForm(form => ({ ...form, batchId: id }));
              setEggSaleForm(form => ({ ...form, batchId: id }));
              loadEggsForBatch(id);
            }}>
              <option value="">Select a layer batch</option>
              {batches.filter(b => b.type === "LAYER").map(b => <option key={b.id} value={b.id}>{b.code}</option>)}
            </select>
          </div>

          {eggBatchId && (
            <>
              <div className="subsection">
                <h3>Egg inventory</h3>
                <div className="table">
                  <div className="row header"><span>Good collected</span><span>Good sold</span><span>Good remaining</span><span>Cracked</span><span>Total collected</span></div>
                  <div className="row">
                    <span>{eggInventory?.goodCollected ?? "—"}</span>
                    <span>{eggInventory?.goodSold ?? "—"}</span>
                    <span>{eggInventory?.goodRemaining ?? "—"}</span>
                    <span>{eggInventory?.crackedCollected ?? "—"}</span>
                    <span>{eggInventory?.totalCollected ?? "—"}</span>
                  </div>
                </div>
              </div>

              <div className="subsection">
                <h3>Record egg production</h3>
                <form className="inline-form" onSubmit={saveEggCollection}>
                  <input required type="date" value={eggCollectionForm.date} onChange={e=>setEggCollectionForm({...eggCollectionForm,date:e.target.value})}/>
                  <input required min="0" step="1" type="number" placeholder="Good eggs" value={eggCollectionForm.good} onChange={e=>setEggCollectionForm({...eggCollectionForm,good:e.target.value})}/>
                  <input required min="0" step="1" type="number" placeholder="Cracked eggs" value={eggCollectionForm.cracked} onChange={e=>setEggCollectionForm({...eggCollectionForm,cracked:e.target.value})}/>
                  <input placeholder="Notes" value={eggCollectionForm.notes} onChange={e=>setEggCollectionForm({...eggCollectionForm,notes:e.target.value})}/>
                  <button disabled={saving}>Record collection</button>
                </form>
                <div className="table">
                  <div className="row header"><span>Date</span><span>Good</span><span>Cracked</span><span>Total</span><span>Notes</span></div>
                  {eggCollections.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.good}</span><span>{row.cracked}</span><span>{row.total}</span><span>{row.notes || "—"}</span></div>)}
                  {eggCollections.length===0 && <p className="muted empty">No egg collections recorded.</p>}
                </div>
              </div>

              <div className="subsection">
                <h3>Egg sales</h3>
                <p className="muted">Current crate size: {crateSize} eggs. The sale stores the crate size used at the time, so changing settings does not rewrite history.</p>
                <form className="inline-form" onSubmit={saveEggSale}>
                  <input required type="date" value={eggSaleForm.date} onChange={e=>setEggSaleForm({...eggSaleForm,date:e.target.value})}/>
                  <select required value={eggSaleForm.customerId} onChange={e=>{const id=e.target.value;const customer=customers.find(c=>c.id===id);setEggSaleForm({...eggSaleForm,customerId:id,customer:customer?.name||""});}}>
                    <option value="">Customer</option>
                    {customers.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                  <input required min="0.001" step="0.001" type="number" placeholder="Crates e.g. 0.5 or 2.5" value={eggSaleForm.crates} onChange={e=>setEggSaleForm({...eggSaleForm,crates:e.target.value})}/>
                  <input required min="1" step="1" type="number" placeholder="Price / crate (minor units)" value={eggSaleForm.pricePerCrateMinor} onChange={e=>setEggSaleForm({...eggSaleForm,pricePerCrateMinor:e.target.value})}/>
                  <button disabled={saving}>Record sale</button>
                </form>
                <div className="table">
                  <div className="row header"><span>Date</span><span>Customer</span><span>Crates</span><span>Eggs</span><span>Price / crate</span><span>Total</span></div>
                  {eggSales.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.customer}</span><span>{row.crates.toString()}</span><span>{row.soldEggs}</span><span>{row.pricePerCrateMinor}</span><span>{row.totalAmountMinor}</span></div>)}
                  {eggSales.length===0 && <p className="muted empty">No egg sales recorded.</p>}
                </div>
              </div>
            </>
          )}
        </section>


        <section className="card full">
          <div className="section-head">
            <h2>Broiler production</h2>
            <p className="muted">Only BROILER batches expose weights, growth, FCR, and bird sales. FCR is calculated by the backend from feed consumed and live-weight gain.</p>
          </div>

          <div className="subsection">
            <h3>Broiler batch</h3>
            <select value={broilerBatchId} onChange={e => {
              const id = e.target.value;
              setBroilerBatchId(id);
              setWeightForm(form => ({ ...form, batchId: id }));
              setBirdSaleForm(form => ({ ...form, batchId: id }));
              setPricingForm(form => ({ ...form, quantity: "" }));
              loadBroilerForBatch(id);
            }}>
              <option value="">Select a broiler batch</option>
              {batches.filter(b => b.type === "BROILER").map(b =>
                <option key={b.id} value={b.id}>{b.code} · {b.status}</option>
              )}
            </select>
          </div>

          {broilerBatchId && (
            <>
              <div className="subsection">
                <h3>Growth summary</h3>
                <div className="table">
                  <div className="row header"><span>Average weight</span><span>Weight gain</span><span>FCR</span><span>Trend records</span></div>
                  <div className="row">
                    <span>{broilerGrowth?.averageWeightKg ?? "—"} kg</span>
                    <span>{broilerGrowth?.weightGainKg ?? "—"} kg</span>
                    <span>{broilerGrowth?.fcr ?? "—"}</span>
                    <span>{broilerGrowth?.trend?.length ?? 0}</span>
                  </div>
                </div>
                <p className="muted">FCR uses kilograms of feed consumed divided by live-weight gain. Feed types must use <strong>kg</strong> to contribute to this calculation.</p>
              </div>

              <div className="subsection">
                <h3>Record weight</h3>
                <form className="inline-form" onSubmit={saveWeight}>
                  <input required type="date" value={weightForm.date} onChange={e=>setWeightForm({...weightForm,date:e.target.value})}/>
                  <input required min="1" type="number" placeholder="Sampled birds" value={weightForm.sampleQuantity} onChange={e=>setWeightForm({...weightForm,sampleQuantity:e.target.value})}/>
                  <input required min="0.001" step="0.001" type="number" placeholder="Total sample weight (kg)" value={weightForm.totalWeightKg} onChange={e=>setWeightForm({...weightForm,totalWeightKg:e.target.value})}/>
                  <input placeholder="Notes" value={weightForm.notes} onChange={e=>setWeightForm({...weightForm,notes:e.target.value})}/>
                  <button disabled={saving}>Record weight</button>
                </form>
                <div className="table">
                  <div className="row header"><span>Date</span><span>Sample</span><span>Total kg</span><span>Average kg</span><span>Gain kg/bird</span></div>
                  {broilerWeights.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.sampleQuantity}</span><span>{row.totalWeightKg}</span><span>{row.averageWeightKg}</span><span>{row.weightGainKg}</span></div>)}
                  {broilerWeights.length===0 && <p className="muted empty">No weight records yet.</p>}
                </div>
              </div>

              <div className="subsection">
                <h3>Cost-based pricing</h3>
                <p className="muted">Actual cost comes from the backend bird-cost ledger. Prices are calculated by Spring Boot.</p>
                <form className="inline-form" onSubmit={e=>{e.preventDefault(); loadPricingForBatch(broilerBatchId, pricingForm.quantity, pricingForm.date);}}>
                  <input required min="1" type="number" placeholder="Bird quantity" value={pricingForm.quantity} onChange={e=>setPricingForm({...pricingForm,quantity:e.target.value})}/>
                  <input required type="date" value={pricingForm.date} onChange={e=>setPricingForm({...pricingForm,date:e.target.value})}/>
                  <button disabled={saving}>Calculate price</button>
                </form>
                {pricing && <div className="table">
                  <div className="row header"><span>Actual cost</span><span>Target margin</span><span>Target price / bird</span><span>Working margin</span><span>Working price / bird</span></div>
                  <div className="row"><span>{pricing.actualCostMinor}</span><span>{pricing.targetMarginPercent ?? "—"}%</span><span>{pricing.targetPricePerBirdMinor ?? "—"}</span><span>{pricing.workingMarginPercent ?? "—"}%</span><span>{pricing.workingPricePerBirdMinor ?? "—"}</span></div>
                </div>}
              </div>

              <div className="subsection">
                <h3>Bird sales</h3>
                <form className="inline-form" onSubmit={saveBirdSale}>
                  <input required type="date" value={birdSaleForm.date} onChange={e=>setBirdSaleForm({...birdSaleForm,date:e.target.value})}/>
                  <input required min="1" type="number" placeholder="Quantity" value={birdSaleForm.quantity} onChange={e=>setBirdSaleForm({...birdSaleForm,quantity:e.target.value})}/>
                  <input required min="1" step="1" type="number" placeholder="Price / bird (minor units)" value={birdSaleForm.pricePerBirdMinor} onChange={e=>setBirdSaleForm({...birdSaleForm,pricePerBirdMinor:e.target.value})}/>
                  <select required value={birdSaleForm.customerId} onChange={e=>{const id=e.target.value;const customer=customers.find(c=>c.id===id);setBirdSaleForm({...birdSaleForm,customerId:id,customer:customer?.name||""});}}>
                    <option value="">Customer</option>
                    {customers.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                  <button disabled={saving}>Record bird sale</button>
                </form>
                <div className="table">
                  <div className="row header"><span>Date</span><span>Quantity</span><span>Price / bird</span><span>Total</span><span>Customer</span></div>
                  {broilerSales.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.quantity}</span><span>{row.pricePerBirdMinor}</span><span>{row.totalAmountMinor}</span><span>{row.customer}</span></div>)}
                  {broilerSales.length===0 && <p className="muted empty">No bird sales recorded.</p>}
                </div>
              </div>
            </>
          )}
        </section>

        <section className="card full">
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
        </section>

        <section className="card full">
          <div className="section-head">
            <h2>Customers</h2>
            <p className="muted">Keep reusable customer records for egg and broiler sales. Type-specific sale records also retain the customer name used when the sale was recorded.</p>
          </div>
          <form className="inline-form" onSubmit={saveCustomer}>
            <input required placeholder="Customer name" value={customerForm.name} onChange={e=>setCustomerForm({...customerForm,name:e.target.value})}/>
            <input placeholder="Phone" value={customerForm.phone} onChange={e=>setCustomerForm({...customerForm,phone:e.target.value})}/>
            <input placeholder="Notes" value={customerForm.notes} onChange={e=>setCustomerForm({...customerForm,notes:e.target.value})}/>
            <button disabled={saving}>Add customer</button>
          </form>
          <div className="table">
            <div className="row header"><span>Name</span><span>Phone</span><span>Notes</span></div>
            {customers.map(row=><div className="row" key={row.id}><span>{row.name}</span><span>{row.phone || "—"}</span><span>{row.notes || "—"}</span></div>)}
            {customers.length===0 && <p className="muted empty">No customers yet.</p>}
          </div>
        </section>

        <section className="card full">
          <div className="section-head">
            <h2>Sales</h2>
            <p className="muted">Common commercial ledger for Layer egg and Broiler bird sales. Type-specific rules remain in their production modules.</p>
          </div>
          <div className="subsection">
            <div className="table">
              <div className="row header"><span>Date</span><span>Type</span><span>Customer</span><span>Batch</span><span>Quantity</span><span>Unit</span><span>Unit price</span><span>Total</span></div>
              {sales.map(row=><div className="row" key={row.id}>
                <span>{row.saleDate}</span>
                <span>{row.saleType}</span>
                <span>{row.customerName}</span>
                <span>{batches.find(b=>b.id===row.batchId)?.code || row.batchId}</span>
                <span>{row.quantity?.toString()}</span>
                <span>{row.unit}</span>
                <span>{row.unitPriceMinor}</span>
                <span>{row.totalAmountMinor}</span>
              </div>)}
              {sales.length===0 && <p className="muted empty">No sales recorded.</p>}
            </div>
          </div>
        </section>

        <section className="card full">
          <div className="section-head">
            <h2>Expenses</h2>
            <p className="muted">Record farm-level or batch-associated expenses. Amounts use integer minor currency units.</p>
          </div>
          <form className="inline-form" onSubmit={saveExpense}>
            <input required type="date" value={expenseForm.occurredDate} onChange={e=>setExpenseForm({...expenseForm,occurredDate:e.target.value})}/>
            <input required placeholder="Description" value={expenseForm.description} onChange={e=>setExpenseForm({...expenseForm,description:e.target.value})}/>
            <input required min="1" step="1" type="number" placeholder="Amount (minor units)" value={expenseForm.amountMinor} onChange={e=>setExpenseForm({...expenseForm,amountMinor:e.target.value})}/>
            <select required value={expenseForm.category} onChange={e=>setExpenseForm({...expenseForm,category:e.target.value})}>
              <option value="OTHER">Other</option>
              <option value="FEED">Feed</option>
              <option value="DRUGS">Drugs</option>
            </select>
            <select value={expenseForm.batchId} onChange={e=>setExpenseForm({...expenseForm,batchId:e.target.value})}>
              <option value="">Farm-level</option>
              {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
            </select>
            <button disabled={saving}>Record expense</button>
          </form>

          <div className="subsection">
            <div className="section-head">
              <h3>Expense history</h3>
              <select value={expenseBatchFilter} onChange={e=>{setExpenseBatchFilter(e.target.value);loadExpenses(e.target.value);}}>
                <option value="">All farm expenses</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select>
            </div>
            <div className="table">
              <div className="row header"><span>Date</span><span>Description</span><span>Amount</span><span>Category</span><span>Association</span></div>
              {expenses.map(row=><div className="row" key={row.id}>
                <span>{row.occurredDate}</span>
                <span>{row.description}</span>
                <span>{row.amountMinor}</span>
                <span>{row.category}</span>
                <span>{row.batchId ? (batches.find(b=>b.id===row.batchId)?.code || row.batchId) : "Farm"}</span>
              </div>)}
              {expenses.length===0 && <p className="muted empty">No expenses recorded.</p>}
            </div>
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