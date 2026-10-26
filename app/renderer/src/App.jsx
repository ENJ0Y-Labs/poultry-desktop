import { useEffect, useState } from "react";
import { todayLocalDate, getLastBatchId, rememberLastBatchId } from "./utils/farmUx.js";
import { parseNairaToKobo } from "./utils/moneyInput.js";
import { formatMoney } from "./utils/money.js";
// eslint-disable-next-line no-unused-vars
import AppShell from "./components/AppShell.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "./components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import { FarmManagerContext } from "./components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import DashboardPage from "./pages/DashboardPage.jsx";
// eslint-disable-next-line no-unused-vars
import FlocksPage from "./pages/FlocksPage.jsx";
// eslint-disable-next-line no-unused-vars
import DailyWorkPage from "./pages/DailyWorkPage.jsx";
// eslint-disable-next-line no-unused-vars
import FeedStockPage from "./pages/FeedStockPage.jsx";
// eslint-disable-next-line no-unused-vars
import HealthPage from "./pages/HealthPage.jsx";
// eslint-disable-next-line no-unused-vars
import ProductionPage from "./pages/ProductionPage.jsx";
// eslint-disable-next-line no-unused-vars
import SalesMoneyPage from "./pages/SalesMoneyPage.jsx";
// eslint-disable-next-line no-unused-vars
import ReportsPage from "./pages/ReportsPage.jsx";
// eslint-disable-next-line no-unused-vars
import SettingsPage from "./pages/SettingsPage.jsx";
function formatQuantity(milli) { return milli == null ? "—" : (Number(milli) / 1000).toLocaleString() + " units"; }
function formatPercent(value) { return value == null ? "—" : Number(value).toFixed(2) + "%"; }
function formatKg(value) { return value == null ? "—" : Number(value).toFixed(2) + " kg"; }
function formatMetric(value) { return value == null ? "—" : Number(value).toFixed(2); }
function formatCount(value) { return value == null ? "—" : Number(value).toLocaleString("en-NG"); }
function displayApiError(err) {
  if (!err) return "";
  if (err.details && typeof err.details === "object") {
    const messages = Object.entries(err.details)
      .map(([field, message]) => `${field}: ${Array.isArray(message) ? message.join(", ") : message}`)
      .filter(Boolean);
    if (messages.length) return messages.join(" • ");
  }
  return err.message || "Something went wrong.";
}

const emptyWeight = {
  batchId: "",
  date: todayLocalDate(),
  sampleQuantity: "",
  totalWeightKg: "",
  notes: "",
};
const emptyBirdSale = {
  batchId: "",
  date: todayLocalDate(),
  quantity: "",
  pricePerBirdMinor: "",
  customer: "",
  customerId: "",
};
const emptyPricing = { quantity: "", date: todayLocalDate() };
import { authApi, batchApi, broilerApi, costApi, dailyApi, eggApi, expenseApi, farmApi, feedApi, populationApi, healthApi, pricingApi, customerApi, salesApi, inventoryApi, dashboardApi, attentionApi, auditApi, backupApi, reportApi, expenseCategoryApi, accountApi, settingsApi, supplierApi } from "./services/api.js";

const emptyFarm = { name: "", location: "", timezone: "Africa/Lagos", currency: "NGN" };
const emptyHouse = { name: "", code: "", notes: "" };
const emptyBatch = {
  type: "LAYER",
  placementDate: todayLocalDate(),
  houseId: "",
  initialBirdCount: "",
  supplierId: "",
  purchaseCostMinor: "",
};
const emptyCost = {
  batchId: "",
  eventDate: todayLocalDate(),
  amountMinor: "",
  reason: "",
};
const emptyFeedType = { name: "", unit: "bag", applicableType: "BOTH" };
const emptyFeedPurchase = {
  feedTypeId: "",
  supplierId: "",
  purchaseDate: todayLocalDate(),
  quantity: "",
  unit: "bag",
  totalCostMinor: "",
};
const emptyFeedUsage = {
  batchId: "",
  feedTypeId: "",
  usageDate: todayLocalDate(),
  quantity: "",
  unit: "bag",
  reason: "",
};
const emptyDaily = {
  batchId: "",
  date: todayLocalDate(),
  notes: "",
  water: "",
};
const emptyPopulationEvent = {
  batchId: "",
  date: todayLocalDate(),
  quantity: "",
  reason: "",
  notes: "",
};
const emptyHealth = {
  batchId: "",
  date: todayLocalDate(),
  conditionProblem: "",
  description: "",
  action: "",
};
const emptyDrug = {
  batchId: "",
  date: todayLocalDate(),
  drug: "",
  quantity: "",
  costMinor: "",
  reason: "",
};
const emptyVaccination = {
  batchId: "",
  date: todayLocalDate(),
  vaccine: "",
  dose: "",
  quantity: "",
  notes: "",
};
const emptyExpense = {
  batchId: "",
  occurredDate: todayLocalDate(),
  description: "",
  amountMinor: "",
  category: "OTHER",
};
const emptyCustomer = { name: "", phone: "", notes: "" };
const emptySupplier = { name: "", phone: "", notes: "" };
const emptyInventoryItem = { name: "", category: "SUPPLY", unit: "piece", reorderLevel: "" };
const emptyInventoryMovement = {
  itemId: "", date: todayLocalDate(), movementType: "RECEIVE",
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
  const [suppliers, setSuppliers] = useState([]);
  const [supplierForm, setSupplierForm] = useState(emptySupplier);
  const [settingsReady, setSettingsReady] = useState(false);
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
    batchId: "", date: todayLocalDate(), good: "", cracked: "", notes: ""
  });
  const [eggSaleForm, setEggSaleForm] = useState({
    batchId: "", date: todayLocalDate(), customer: "", customerId: "", crates: "", pricePerCrateMinor: ""
  });
  const [houseForm, setHouseForm] = useState(emptyHouse);
  const [crateSize, setCrateSize] = useState(30);
  const [defaultWater, setDefaultWater] = useState("");
  const [waterSizes, setWaterSizes] = useState("25, 75");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [authStatus, setAuthStatus] = useState(null);
  const [authForm, setAuthForm] = useState({ email: "", fullName: "", password: "" });
  const [dashboard, setDashboard] = useState(null);
  const [attention, setAttention] = useState([]);
  const [auditRows, setAuditRows] = useState([]);
  const [expenseCategories, setExpenseCategories] = useState([]);
  const [expenseCategoryForm, setExpenseCategoryForm] = useState("");
  const [account, setAccount] = useState(null);
  const [accountForm, setAccountForm] = useState({ email: "", fullName: "", currentPassword: "", newPassword: "" });
  const [applicationSettings, setApplicationSettings] = useState({ startPage: "dashboard", dateFormat: "YYYY-MM-DD" });
  const [backupSettings, setBackupSettings] = useState({ enabled: false, directory: "", intervalMs: 86400000 });
  const [selectedBatchId, setSelectedBatchId] = useState(() => getLastBatchId() || "");
  const [batchDashboard, setBatchDashboard] = useState(null);
  const [farmReport, setFarmReport] = useState(null);
  const [batchReport, setBatchReport] = useState(null);
  const [reportAsOf, setReportAsOf] = useState(todayLocalDate());

  async function load({ showLoading = true, preserveForms = false } = {}) {
    if (showLoading) setLoading(true);
    setError("");
    try {
      const currentFarm = await farmApi.get();
      setFarm(currentFarm);
      if (!preserveForms) setFarmForm({name:currentFarm.name,location:currentFarm.location||"",timezone:currentFarm.timezone,currency:currentFarm.currency});
      const settings = await farmApi.getSettings();
      if (!preserveForms) {
        setCrateSize(settings.defaultCrateSize);
        setDefaultWater(settings.defaultWaterContainerSize ?? "");
        setWaterSizes(settings.waterContainerSizes.join(", "));
      }
      const currentHouses = await farmApi.listHouses();
      setHouses(currentHouses);
      setBatchForm(form => ({ ...form, houseId: form.houseId || currentHouses[0]?.id || "" }));
      const currentBatches = await batchApi.list();
      setBatches(currentBatches);
      const lastBatchId = getLastBatchId();
      const preferredBatchId = currentBatches.some(batch => batch.id === lastBatchId) ? lastBatchId : currentBatches[0]?.id || "";
      if (preferredBatchId) {
        rememberLastBatchId(preferredBatchId);
        setSelectedBatchId(preferredBatchId);
        setCostForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setDailyForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setMortalityForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setCullingForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setHealthForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setDrugForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setVaccinationForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setExpenseForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
        setFeedUsageForm(form => ({ ...form, batchId: form.batchId || preferredBatchId }));
      }
      setExpenses(await expenseApi.list());
      setCustomers(await customerApi.list());
      setSuppliers(await supplierApi.list());
      setSales(await salesApi.list());
      setInventoryItems(await inventoryApi.listItems());
      setFeedTypes(await feedApi.listTypes());
      setFeedInventory(await feedApi.inventory());
      setPricingSettings(await pricingApi.settings());
      const [categories, currentAccount, appSettings, backupConfig] = await Promise.all([
        expenseCategoryApi.list(), accountApi.get(), settingsApi.application(), settingsApi.backup()
      ]);
      setExpenseCategories(categories);
      setAccount(currentAccount);
      if (!preserveForms) setAccountForm({ email: currentAccount.email, fullName: currentAccount.fullName, currentPassword: "", newPassword: "" });
      if (!preserveForms) {
        setApplicationSettings({ ...appSettings, startPage: appSettings.startPage === "operations" ? "daily-work" : appSettings.startPage });
        setBackupSettings(backupConfig);
      }
      setSettingsReady(true);
      return true;
    } catch (err) {
      if (err.code === "UNAUTHENTICATED") {
        setAuthStatus({ authenticated: false, setupRequired: false });
      } else if (err.message !== "No farm has been created yet.") {
        setError(displayApiError(err));
      }
      return false;
    } finally {
      if (showLoading) setLoading(false);
    }
  }
  async function initialize({ preserveScreen = false } = {}) {
    if (!preserveScreen) setLoading(true);
    setError("");
    try {
      const status = await authApi.status();
      setAuthStatus(status);
      if (status.authenticated) {
        return await load({ showLoading: !preserveScreen, preserveForms: preserveScreen });
      }
      if (!preserveScreen) setLoading(false);
      return true;
    } catch (err) {
      setError(displayApiError(err));
      if (!preserveScreen) setLoading(false);
      return false;
    }
  }
  async function submitAuth(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      if (authStatus?.setupRequired) {
        await authApi.setup(authForm);
        setAuthStatus({ authenticated: false, setupRequired: false });
      }
      await authApi.login({ email: authForm.email, password: authForm.password });
      setAuthStatus({ authenticated: true, setupRequired: false });
      await load();
    } catch (err) {
      setError(displayApiError(err));
    } finally { setSaving(false); }
  }
  async function loadReport(asOf = reportAsOf) {
    try {
      setFarmReport(await reportApi.farm(asOf));
      if (selectedBatchId) setBatchReport(await reportApi.batch(selectedBatchId, asOf));
    } catch (err) { setError(displayApiError(err)); }
  }

  async function loadBatchReport(id = selectedBatchId, asOf = reportAsOf) {
    if (!id) { setBatchReport(null); return; }
    try { setBatchReport(await reportApi.batch(id, asOf)); } catch (err) { setError(displayApiError(err)); }
  }

  async function exportCsv(loader, filename) {
    try {
      const csv = await loader();
      const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(displayApiError(err));
    }
  }

  async function exportPdf(loader, filename) {
    try {
      const blob = await loader();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(displayApiError(err));
    }
  }

  async function loadBatchDashboard(id = selectedBatchId) {
    if (!id) { setBatchDashboard(null); return; }
    try { setBatchDashboard(await dashboardApi.batch(id)); } catch (err) { setError(displayApiError(err)); }
  }

  async function loadDashboard() {
    if (!farm) return;
    try {
      const [summary, attentionRows, audit] = await Promise.all([
        dashboardApi.farm(),
        attentionApi.list(),
        auditApi.list(20),
      ]);
      setDashboard(summary);
      setAttention(attentionRows);
      setAuditRows(audit);
    } catch (err) {
      setError(displayApiError(err));
    }
  }

  useEffect(() => { initialize(); }, []);
  useEffect(() => { if (farm) { loadDashboard(); loadReport(); } }, [farm, batches.length, inventoryItems.length, sales.length, expenses.length]);
  useEffect(() => {
    if (selectedBatchId && batches.some(batch => batch.id === selectedBatchId)) {
      loadBatchDashboard(selectedBatchId);
      loadBatchReport(selectedBatchId);
    } else {
      setBatchReport(null);
    }
  }, [selectedBatchId, batches.length]);

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
      setSuppliers([]);
      setSettingsReady(true);
      setSales([]);
      setInventoryItems([]);
      setInventoryMovements([]);
      setFeedTypes([]);
      setFeedInventory([]);
      setBatchForm(emptyBatch);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function saveFarm(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { setFarm(await farmApi.update(farmForm)); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function createExpenseCategory(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { const created = await expenseCategoryApi.create({ name: expenseCategoryForm.trim() }); setExpenseCategories(rows => [...rows, created].sort((a,b) => a.name.localeCompare(b.name))); setExpenseCategoryForm(""); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }
  async function archiveExpenseCategory(id) {
    setSaving(true); setError("");
    try { await expenseCategoryApi.archive(id); setExpenseCategories(rows => rows.filter(row => row.id !== id)); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }
  async function saveAccount(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { const updated = await accountApi.update(accountForm); setAccount(updated); setAccountForm({ email: updated.email, fullName: updated.fullName, currentPassword: "", newPassword: "" }); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }
  async function saveApplicationSettings(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { setApplicationSettings(await settingsApi.updateApplication(applicationSettings)); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }
  async function chooseBackupSettingsDirectory() {
    try { const directory = window.poultryDesktop?.chooseBackupDirectory ? await window.poultryDesktop.chooseBackupDirectory() : null; if (directory) setBackupSettings(s => ({ ...s, directory })); }
    catch (err) { setError(displayApiError(err)); }
  }
  async function saveBackupSettings(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { setBackupSettings(await settingsApi.updateBackup(backupSettings)); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function createBackup() {
    setSaving(true); setError("");
    try {
      const directory = window.poultryDesktop?.chooseBackupDirectory ? await window.poultryDesktop.chooseBackupDirectory() : null;
      if (!directory) return;
      await backupApi.create(directory);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function restoreBackup() {
    setSaving(true); setError("");
    try { await window.poultryDesktop?.restoreBackup?.(); await load(); } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function loadExpenses(batchId = expenseBatchFilter) {
    try {
      setExpenses(await expenseApi.list(batchId || null));
    } catch (err) { setError(displayApiError(err)); }
  }
  async function loadSales() {
    try { setSales(await salesApi.list()); }
    catch (err) { setError(displayApiError(err)); }
  }

  async function saveSupplier(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await supplierApi.create({
        name: supplierForm.name.trim(),
        phone: supplierForm.phone.trim() || null,
        notes: supplierForm.notes.trim() || null,
      });
      setSuppliers(current => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setSupplierForm(emptySupplier);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function archiveSupplier(id) {
    setSaving(true); setError("");
    try { await supplierApi.archive(id); setSuppliers(current => current.filter(supplier => supplier.id !== id)); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function loadInventoryMovements(itemId = inventoryMovementForm.itemId) {
    if (!itemId) { setInventoryMovements([]); return; }
    try { setInventoryMovements(await inventoryApi.listMovements(itemId)); }
    catch (err) { setError(displayApiError(err)); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function saveExpense(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await expenseApi.create({
        occurredDate: expenseForm.occurredDate,
        description: expenseForm.description.trim(),
        amountMinor: parseNairaToKobo(expenseForm.amountMinor),
        category: expenseForm.category,
        batchId: expenseForm.batchId || null,
      });
      setExpenses(current => [created, ...current]);
      setExpenseForm(form => ({ ...emptyExpense, batchId: form.batchId }));
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
        purchaseCostMinor: parseNairaToKobo(batchForm.purchaseCostMinor, { allowZero: true }),
      });
      setBatches(current => [created, ...current]);
      rememberLastBatchId(created.id);
      setSelectedBatchId(created.id);
      setBatchForm({ ...emptyBatch, houseId: batchForm.houseId });
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function addBirdCost(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await costApi.add(costForm.batchId, {
        eventDate: costForm.eventDate,
        amountMinor: parseNairaToKobo(costForm.amountMinor),
        reason: costForm.reason.trim() || null,
      });
      setCostForm(form => ({ ...emptyCost, batchId: form.batchId }));
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function createFeedType(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await feedApi.createType(feedTypeForm);
      setFeedTypes(current => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setFeedTypeForm(emptyFeedType);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
        totalCostMinor: parseNairaToKobo(feedPurchaseForm.totalCostMinor),
      });
      setFeedPurchaseForm(form => ({ ...emptyFeedPurchase, feedTypeId: form.feedTypeId }));
      setFeedInventory(await feedApi.inventory());
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }


  async function loadPricingForBatch(batchId, quantity, date) {
    if (!batchId || !quantity) { setPricing(null); return; }
    try { setPricing(await pricingApi.price(batchId, Number(quantity), date || undefined)); }
    catch (err) { setError(displayApiError(err)); }
  }

  async function savePricingSettings(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const updated = await pricingApi.updateSettings({
        targetMarginPercent: pricingSettings.targetMarginPercent === "" ? null : pricingSettings.targetMarginPercent
      });
      setPricingSettings(updated);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function saveBirdSale(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const batchId = birdSaleForm.batchId;
      const sale = {
        recordDate: birdSaleForm.date,
        quantity: Number(birdSaleForm.quantity),
        pricePerBirdMinor: parseNairaToKobo(birdSaleForm.pricePerBirdMinor),
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
        pricePerCrateMinor: parseNairaToKobo(eggSaleForm.pricePerCrateMinor),
      });
      await loadEggsForBatch(batchId);
      setEggSaleForm(form => ({ ...form, customer: "", customerId: "", crates: "", pricePerCrateMinor: "" }));
      await loadSales();
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function saveDrug(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await healthApi.addDrug(drugForm.batchId, {
        recordDate: drugForm.date,
        drug: drugForm.drug.trim(),
        quantity: Number(drugForm.quantity),
        costMinor: parseNairaToKobo(drugForm.costMinor),
        reason: drugForm.reason.trim(),
      });
      setDrugRecords(await healthApi.listDrugs(drugForm.batchId));
      setDrugForm(form => ({ ...emptyDrug, batchId: form.batchId }));
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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
    } catch (err) { setError(displayApiError(err)); }
  }

  async function markBatchSold(id) {
    setSaving(true); setError("");
    try {
      const updated = await batchApi.markSold(id);
      setBatches(current => current.map(batch => batch.id === id ? updated : batch));
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function reopenBatch(id) {
    const reason = window.prompt("Reason for reopening this SOLD batch:");
    if (reason === null) return;
    setSaving(true); setError("");
    try {
      const updated = await batchApi.reopen(id, reason);
      setBatches(current => current.map(batch => batch.id === id ? updated : batch));
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function addHouse(event) {
    event.preventDefault(); setSaving(true); setError("");
    try { await farmApi.createHouse(houseForm); setHouseForm(emptyHouse); setHouses(await farmApi.listHouses()); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  if (loading) return <main className="shell"><p className="muted">Loading farm...</p></main>;

  if (!authStatus?.authenticated) return (
    <main className="shell narrow">
      <header className="hero">
        <p className="eyebrow">GRANTINO FARMS</p>
        <h1>{authStatus?.setupRequired ? "Create owner account" : "Sign in"}</h1>
        <p className="muted">{authStatus?.setupRequired
          ? "Create the local owner account to secure and access your farm."
          : "Sign in to access your local farm data."}</p>
      </header>
      {error && <div className="error" role="alert">{error}</div>}
      {authStatus ? (
        <form className="card form-grid" onSubmit={submitAuth}>
          {authStatus.setupRequired && <label>Full name<input required maxLength="120" autoComplete="name" value={authForm.fullName} onChange={e=>setAuthForm({...authForm,fullName:e.target.value})}/></label>}
          <label>Email<input required type="email" autoComplete="username" value={authForm.email} onChange={e=>setAuthForm({...authForm,email:e.target.value})}/></label>
          <label>Password<input required type="password" minLength={authStatus.setupRequired ? 10 : undefined} autoComplete={authStatus.setupRequired ? "new-password" : "current-password"} value={authForm.password} onChange={e=>setAuthForm({...authForm,password:e.target.value})}/>{authStatus.setupRequired && <small>Use at least 10 characters.</small>}</label>
          <button disabled={saving} type="submit">{saving ? "Please wait..." : authStatus.setupRequired ? "Create account and sign in" : "Sign in"}</button>
        </form>
      ) : (
        <button type="button" onClick={initialize}>Retry connection</button>
      )}
    </main>
  );

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

  const pageModel = { suppliers, supplierForm, setSupplierForm, saveSupplier, archiveSupplier, formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, displayApiError, emptyWeight, emptyBirdSale, emptyPricing, emptyFarm, emptyHouse, emptyBatch, emptyCost, emptyFeedType, emptyFeedPurchase, emptyFeedUsage, emptyDaily, emptyPopulationEvent, emptyHealth, emptyDrug, emptyVaccination, emptyExpense, emptyCustomer, emptyInventoryItem, emptyInventoryMovement, farm, setFarm, farmForm, setFarmForm, houses, setHouses, batches, setBatches, batchForm, setBatchForm, costForm, setCostForm, feedTypes, setFeedTypes, feedInventory, setFeedInventory, feedTypeForm, setFeedTypeForm, feedPurchaseForm, setFeedPurchaseForm, feedUsageForm, setFeedUsageForm, dailyForm, setDailyForm, dailyRecord, setDailyRecord, mortalityForm, setMortalityForm, cullingForm, setCullingForm, healthForm, setHealthForm, drugForm, setDrugForm, vaccinationForm, setVaccinationForm, healthRecords, setHealthRecords, drugRecords, setDrugRecords, vaccinationRecords, setVaccinationRecords, expenseForm, setExpenseForm, expenses, setExpenses, expenseBatchFilter, setExpenseBatchFilter, inventoryItems, setInventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, setInventoryMovements, customers, setCustomers, customerForm, setCustomerForm, sales, setSales, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, setBroilerGrowth, broilerWeights, setBroilerWeights, broilerSales, setBroilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, pricingSettings, setPricingSettings, pricing, setPricing, pricingForm, setPricingForm, eggInventory, setEggInventory, eggCollections, setEggCollections, eggSales, setEggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, houseForm, setHouseForm, crateSize, setCrateSize, defaultWater, setDefaultWater, waterSizes, setWaterSizes, loading, setLoading, saving, setSaving, error, setError, dashboard, setDashboard, attention, setAttention, auditRows, setAuditRows, expenseCategories, setExpenseCategories, expenseCategoryForm, setExpenseCategoryForm, account, setAccount, accountForm, setAccountForm, applicationSettings, setApplicationSettings, backupSettings, setBackupSettings, selectedBatchId, setSelectedBatchId, batchDashboard, setBatchDashboard, farmReport, setFarmReport, batchReport, setBatchReport, reportAsOf, setReportAsOf, load, loadReport, loadBatchReport, exportCsv, exportPdf, loadBatchDashboard, loadDashboard, submitFarm, saveFarm, createExpenseCategory, archiveExpenseCategory, saveAccount, saveApplicationSettings, chooseBackupSettingsDirectory, saveBackupSettings, createBackup, restoreBackup, saveSettings, loadExpenses, loadSales, saveCustomer, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, saveExpense, createBatch, addBirdCost, createFeedType, purchaseFeed, useFeed, feedQuantity, parseWaterEntries, saveDailyRecord, recordPopulationEvent, loadPricingForBatch, savePricingSettings, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale, saveHealth, saveDrug, saveVaccination, loadHealthForBatch, markBatchSold, reopenBatch, addHouse };

  return (
    <AppShell farm={farm} error={error} onRetry={() => initialize({ preserveScreen: true })} startPage={applicationSettings.startPage} startPageReady={settingsReady}>
      <FarmManagerContext.Provider value={pageModel}>
        <PageSection page="dashboard"><DashboardPage /></PageSection>
        <PageSection page="flocks"><FlocksPage /></PageSection>
        <PageSection page="daily-work"><DailyWorkPage /></PageSection>
        <PageSection page="feed-stock"><FeedStockPage /></PageSection>
        <PageSection page="health"><HealthPage /></PageSection>
        <PageSection page="production"><ProductionPage /></PageSection>
        <PageSection page="sales-money"><SalesMoneyPage /></PageSection>
        <PageSection page="reports"><ReportsPage /></PageSection>
        <PageSection page="settings"><SettingsPage /></PageSection>
      </FarmManagerContext.Provider>
    </AppShell>
  );
}