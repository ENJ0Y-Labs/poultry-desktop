import { useEffect, useRef, useState } from "react";
import { useToast } from "./components/ToastProvider.jsx";
import pomasLogo from "./assets/pomas-logo.png";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ValidatedForm from "./components/ValidatedForm.jsx";
import { todayLocalDate, getLastBatchId, rememberLastBatchId } from "./utils/farmUx.js";
import { parseNairaToKobo } from "./utils/moneyInput.js";
import { getActiveValidationForm } from "./utils/formValidation.js";
import { formatMoney } from "./utils/money.js";
import { appVersionLabel } from "./utils/appInfo.js";
import { getThemePreference, setThemePreference } from "./utils/theme.js";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ThemePreferenceSwitches from "./components/ThemePreferenceSwitches.jsx";
import { parseAttentionContext } from "./utils/attentionNavigation.js";
import { createDateRange, isValidDateRange } from "./utils/dateRange.js";
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
// eslint-disable-next-line no-unused-vars
import ImportExportPage from "./pages/ImportExportPage.jsx";
function formatQuantity(milli) { return milli == null ? "—" : (Number(milli) / 1000).toLocaleString() + " units"; }
function formatPercent(value) { return value == null ? "—" : Number(value).toFixed(2) + "%"; }
function formatKg(value) { return value == null ? "—" : Number(value).toFixed(2) + " kg"; }
function formatMetric(value) { return value == null ? "—" : Number(value).toFixed(2); }
function formatCount(value) { return value == null ? "—" : Number(value).toLocaleString("en-NG"); }
function displayApiError(err) {
  if (!err) return "";
  if ((err.details && typeof err.details === "object") || err.code === "SUPPLIER_DUPLICATE") {
    const details = err.details || { fields: { name: err.message } };
    const activeForm = getActiveValidationForm();
    if (activeForm) window.dispatchEvent(new CustomEvent("pomas:form-validation-error", { detail: { form: activeForm, details } }));
    const fieldDetails = details.fields && typeof details.fields === "object" ? details.fields : details;
    const messages = Object.entries(fieldDetails)
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
const emptyHouse = { name: "", code: "", notes: "", houseType: "PERMANENT" };
const emptyBatch = {
  type: "LAYER",
  placementDate: todayLocalDate(),
  houseId: "",
  initialBirdCount: "",
  supplierId: "",
  purchaseCostRecorded: true,
  purchaseCostMinor: "",
  populationCountStatus: "ESTIMATED",
};
const emptyCost = {
  batchId: "",
  eventDate: todayLocalDate(),
  amountMinor: "",
  reason: "",
};
const emptyFeedType = { name: "", brand: "", unit: "bag", packagingName: "bag", packageWeightKg: "", applicableTypes: ["LAYER", "BROILER", "TURKEY"], kgPerUnit: "" };
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
const emptyPopulationAdjustment = { effectiveDate: todayLocalDate(), newCount: "", reason: "" };
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
const emptySupplier = { name: "", phone: "", email: "", address: "", notes: "" };
const emptyInventoryItem = { name: "", category: "SUPPLY", unit: "piece", reorderLevel: "" };
const emptyInventoryMovement = {
  itemId: "", date: todayLocalDate(), movementType: "RECEIVE",
  quantity: "", reason: "", source: "", batchId: ""
};

export default function App() {
  const notify = useToast();
  const [themePreference, setThemePreferenceState] = useState(getThemePreference);
  const [farm, setFarm] = useState(null);
  const [farmForm, setFarmForm] = useState(emptyFarm);
  const [houses, setHouses] = useState([]);
  const [batches, setBatches] = useState([]);
  const [batchForm, setBatchForm] = useState(emptyBatch);
  const [editingBatchId, setEditingBatchId] = useState("");
  const [batchSetupForm, setBatchSetupForm] = useState({ houseId: "", supplierId: "" });
  const [costForm, setCostForm] = useState(emptyCost);
  const [feedTypes, setFeedTypes] = useState([]);
  const [feedInventory, setFeedInventory] = useState([]);
  const [feedTypeForm, setFeedTypeForm] = useState(emptyFeedType);
  const [editingFeedTypeId, setEditingFeedTypeId] = useState("");
  const [feedPurchaseForm, setFeedPurchaseForm] = useState(emptyFeedPurchase);
  const [feedUsageForm, setFeedUsageForm] = useState(emptyFeedUsage);
  const [dailyForm, setDailyForm] = useState(emptyDaily);
  const [dailyRecord, setDailyRecord] = useState(null);
  const [dailyRecordLoading, setDailyRecordLoading] = useState(false);
  const [dailyRecordReloadKey, setDailyRecordReloadKey] = useState(0);
  const [mortalityForm, setMortalityForm] = useState(emptyPopulationEvent);
  const [mortalityModalSignal, setMortalityModalSignal] = useState(0);
  const [feedUsageModalSignal, setFeedUsageModalSignal] = useState(0);
  const [feedPurchaseModalSignal, setFeedPurchaseModalSignal] = useState(0);
  const [cullingForm, setCullingForm] = useState(emptyPopulationEvent);
  const [populationAdjustmentForm, setPopulationAdjustmentForm] = useState(emptyPopulationAdjustment);
  const [populationAdjustments, setPopulationAdjustments] = useState([]);
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
  const [editingInventoryItemId, setEditingInventoryItemId] = useState("");
  const [inventoryMovementForm, setInventoryMovementForm] = useState(emptyInventoryMovement);
  const [inventoryMovements, setInventoryMovements] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [supplierForm, setSupplierForm] = useState(emptySupplier);
  const [editingSupplierId, setEditingSupplierId] = useState("");
  const [settingsReady, setSettingsReady] = useState(false);
  const [customerForm, setCustomerForm] = useState(emptyCustomer);
  const [editingCustomerId, setEditingCustomerId] = useState("");
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
  const [editingHouseId, setEditingHouseId] = useState("");
  const [crateSize, setCrateSize] = useState(30);
  const [defaultWater, setDefaultWater] = useState("");
  const [waterSizes, setWaterSizes] = useState("25, 75");
  const [waterUnitLabel, setWaterUnitLabel] = useState("litres");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [dataLoadError, setDataLoadError] = useState("");
  const [authStatus, setAuthStatus] = useState(null);
  const [showAuthPassword, setShowAuthPassword] = useState(false);
  const [authForm, setAuthForm] = useState({ username: "", identifier: "", email: "", fullName: "", password: "", rememberMe: false });
  const [dashboard, setDashboard] = useState(null);
  const [attention, setAttention] = useState([]);
  const [auditRows, setAuditRows] = useState([]);
  const [expenseCategories, setExpenseCategories] = useState([]);
  const [expenseCategoryForm, setExpenseCategoryForm] = useState("");
  const [account, setAccount] = useState(null);
  const [accountForm, setAccountForm] = useState({ username: "", email: "", fullName: "", currentPassword: "", newPassword: "" });
  const [applicationSettings, setApplicationSettings] = useState({ startPage: "dashboard", dateFormat: "YYYY-MM-DD" });
  const [backupSettings, setBackupSettings] = useState({ enabled: false, directory: "", intervalMs: 86400000 });
  const [selectedBatchId, setSelectedBatchId] = useState(() => getLastBatchId() || "");
  const [batchDashboard, setBatchDashboard] = useState(null);
  const [batchDashboardLoading, setBatchDashboardLoading] = useState(false);
  const [farmReport, setFarmReport] = useState(null);
  const [batchReport, setBatchReport] = useState(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [batchReportLoading, setBatchReportLoading] = useState(false);
  const [reportAsOf, setReportAsOf] = useState(todayLocalDate());
  const [reportExporting, setReportExporting] = useState(false);
  const [drafts, setDrafts] = useState({});
  const [draftStatuses, setDraftStatuses] = useState({});
  const [draftsReady, setDraftsReady] = useState(false);
  const draftRestoreStarted = useRef(false);
  const [dateRange, setDateRange] = useState(() => createDateRange("LAST_30_DAYS", "Africa/Lagos"));
  const dashboardRequestSequence = useRef(0);
  const farmReportRequestSequence = useRef(0);
  const batchReportRequestSequence = useRef(0);
  const [appInfo, setAppInfo] = useState({ version: null, architecture: null, isPackaged: !import.meta.env.DEV, customTitleBar: Boolean(window.poultryDesktop?.supportsCustomTitleBar) });


  function draftReferences(form) {
    const snapshot = record => record ? {
      id: record.id, name: record.name, code: record.code, type: record.type,
      status: record.status, unit: record.unit, brand: record.brand,
      houseId: record.houseId, houseType: record.houseType, placementDate: record.placementDate,
      initialBirdCount: record.initialBirdCount, populationCountStatus: record.populationCountStatus,
      phone: record.phone, email: record.email, applicableTypes: record.applicableTypes
    } : null;
    return {
      batch: snapshot(batches.find(item => item.id === (form.batchId || ""))),
      house: snapshot(houses.find(item => item.id === (form.houseId || ""))),
      feedType: snapshot(feedTypes.find(item => item.id === (form.feedTypeId || ""))),
      supplier: snapshot(suppliers.find(item => item.id === (form.supplierId || ""))),
      customer: snapshot(customers.find(item => item.id === (form.customerId || "")))
    };
  }

  function draftIsMeaningful(id, form) {
    const fields = {
      batch: ["initialBirdCount", "purchaseCostMinor"],
      feedPurchase: ["feedTypeId", "quantity", "totalCostMinor", "supplierId"],
      expense: ["description", "amountMinor"],
      health: ["conditionProblem", "description", "action"],
      drug: ["drug", "quantity", "costMinor", "reason"],
      vaccination: ["vaccine", "dose", "quantity", "notes"],
      birdSale: ["quantity", "pricePerBirdMinor", "customer", "customerId"],
      eggSale: ["crates", "pricePerCrateMinor", "customer", "customerId"],
      eggCollection: ["good", "cracked", "notes"],
      weight: ["sampleQuantity", "totalWeightKg", "notes"]
    };
    return (fields[id] || []).some(key => String(form?.[key] ?? "").trim() !== "");
  }

  function getDraftForm(id) {
    return ({
      batch: batchForm, feedPurchase: feedPurchaseForm, expense: expenseForm,
      health: healthForm, drug: drugForm, vaccination: vaccinationForm,
      birdSale: birdSaleForm, eggSale: eggSaleForm, eggCollection: eggCollectionForm,
      weight: weightForm
    })[id];
  }

  async function discardDraft(id, resetForm = true) {
    try {
      await window.poultryDesktop?.discardDraft?.(id);
      setDrafts(current => { const next = { ...current }; delete next[id]; return next; });
      setDraftStatuses(current => { const next = { ...current }; delete next[id]; return next; });
      const resetters = {
        batch: () => setBatchForm(emptyBatch),
        feedPurchase: () => setFeedPurchaseForm(emptyFeedPurchase),
        expense: () => setExpenseForm(emptyExpense),
        health: () => setHealthForm(emptyHealth),
        drug: () => setDrugForm(emptyDrug),
        vaccination: () => setVaccinationForm(emptyVaccination),
        birdSale: () => setBirdSaleForm(emptyBirdSale),
        eggSale: () => setEggSaleForm({ batchId: eggBatchId, date: todayLocalDate(), customer: "", customerId: "", crates: "", pricePerCrateMinor: "" }),
        eggCollection: () => setEggCollectionForm({ batchId: eggBatchId, date: todayLocalDate(), good: "", cracked: "", notes: "" }),
        weight: () => setWeightForm(emptyWeight)
      };
      if (resetForm) resetters[id]?.();
    } catch (err) {
      if (resetForm) {
        setError(displayApiError(err));
      } else {
        setDraftStatuses(current => ({ ...current, [id]: { ...current[id], hasDraft: true, saving: false, posted: true } }));
        throw new Error("The transaction was recorded, but its draft could not be cleared. Do not submit this form again. Use Discard draft to remove it.");
      }
    }
  }

  async function markDraftReviewed(id) {
    const form = getDraftForm(id);
    if (!form || !window.poultryDesktop?.saveDraft) return;
    try {
      const saved = await window.poultryDesktop.saveDraft({ id, form, references: draftReferences(form) });
      setDrafts(current => ({ ...current, [id]: saved }));
      setDraftStatuses(current => ({ ...current, [id]: { hasDraft: true, saving: false, stale: false, staleReferences: [], savedAt: saved.savedAt } }));
    } catch (err) {
      setError("Draft review could not be saved: " + displayApiError(err));
    }
  }

  useEffect(() => {
    if (!authStatus?.authenticated || !settingsReady || draftRestoreStarted.current) return undefined;
    draftRestoreStarted.current = true;
    let active = true;
    (async () => {
      try {
        const saved = await window.poultryDesktop?.listDrafts?.() || {};
        if (!active) return;
        const setters = {
          batch: setBatchForm, feedPurchase: setFeedPurchaseForm, expense: setExpenseForm,
          health: setHealthForm, drug: setDrugForm, vaccination: setVaccinationForm,
          birdSale: setBirdSaleForm, eggSale: setEggSaleForm,
          eggCollection: setEggCollectionForm, weight: setWeightForm
        };
        const statuses = {};
        for (const [id, draft] of Object.entries(saved)) {
          if (!setters[id] || !draft?.form) continue;
          setters[id](draft.form);
          const currentReferences = draftReferences(draft.form);
          const staleReferences = Object.keys(currentReferences).filter(key =>
            JSON.stringify(currentReferences[key]) !== JSON.stringify(draft.references?.[key] ?? null)
          );
          statuses[id] = { hasDraft: true, saving: false, stale: staleReferences.length > 0, staleReferences, savedAt: draft.savedAt || null };
        }
        setDrafts(saved);
        setDraftStatuses(statuses);
      } catch {
        // Draft recovery must not prevent the farm workspace from opening.
      } finally {
        if (active) setDraftsReady(true);
      }
    })();
    return () => { active = false; };
  // Draft restoration runs once after the authenticated farm collections are loaded.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authStatus?.authenticated, settingsReady]);

  useEffect(() => {
    if (!draftsReady || !authStatus?.authenticated) return undefined;
    const entries = [
      ["batch", batchForm], ["feedPurchase", feedPurchaseForm], ["expense", expenseForm],
      ["health", healthForm], ["drug", drugForm], ["vaccination", vaccinationForm],
      ["birdSale", birdSaleForm], ["eggSale", eggSaleForm],
      ["eggCollection", eggCollectionForm], ["weight", weightForm]
    ];
    const timer = setTimeout(async () => {
      for (const [id, form] of entries) {
        if (!draftIsMeaningful(id, form) || !window.poultryDesktop?.saveDraft || draftStatuses[id]?.saving || draftStatuses[id]?.posted) continue;
        if (draftStatuses[id]?.saveError && JSON.stringify(draftStatuses[id]?.failedForm) === JSON.stringify(form)) continue;
        const existingDraft = drafts[id];
        if (existingDraft && JSON.stringify(existingDraft.form) === JSON.stringify(form)) continue;
        setDraftStatuses(current => ({ ...current, [id]: { ...current[id], hasDraft: true, saving: true } }));
        try {
          const existing = drafts[id];
          const references = draftStatuses[id]?.stale && existing?.references
            ? existing.references : draftReferences(form);
          const saved = await window.poultryDesktop.saveDraft({ id, form, references });
          setDrafts(current => ({ ...current, [id]: saved }));
          setDraftStatuses(current => ({
            ...current,
            [id]: { hasDraft: true, saving: false, stale: current[id]?.stale || false, staleReferences: current[id]?.staleReferences || [], savedAt: saved.savedAt }
          }));
        } catch {
          setDraftStatuses(current => ({ ...current, [id]: { ...current[id], saving: false, saveError: true, failedForm: form } }));
        }
      }
    }, 700);
    return () => clearTimeout(timer);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftsReady, authStatus?.authenticated, batchForm, feedPurchaseForm, expenseForm, healthForm, drugForm, vaccinationForm, birdSaleForm, eggSaleForm, eggCollectionForm, weightForm, drafts, draftStatuses, batches, houses, feedTypes, suppliers, customers]);

  useEffect(() => {
    if (!draftsReady) return;
    setDraftStatuses(current => {
      const next = { ...current };
      for (const [id, draft] of Object.entries(drafts)) {
        if (!draft?.form || !next[id]?.hasDraft) continue;
        const stale = JSON.stringify(draftReferences(draft.form)) !== JSON.stringify(draft.references || {});
        if (stale !== next[id].stale) next[id] = { ...next[id], stale };
      }
      return next;
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftsReady, drafts, batches, houses, feedTypes, suppliers, customers]);

  useEffect(() => {
    let active = true;
    window.poultryDesktop?.getAppInfo?.()
      .then(info => { if (active && info && typeof info.version === "string") setAppInfo(info); })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  function changeThemePreference(preference) {
    const nextTheme = setThemePreference(preference);
    setThemePreferenceState(nextTheme);
  }

  function updateDateRange(nextRange) {
    const boundariesChanged = nextRange.from !== dateRange.from || nextRange.to !== dateRange.to;
    setDateRange(nextRange);
    if (boundariesChanged || !isValidDateRange(nextRange)) {
      dashboardRequestSequence.current += 1;
      farmReportRequestSequence.current += 1;
      batchReportRequestSequence.current += 1;
      setDashboard(null);
      setFarmReport(null);
      setBatchReport(null);
      setAuditRows([]);
    }
  }

  async function load({ showLoading = true, preserveForms = false } = {}) {
    if (showLoading) setLoading(true);
    setDataLoadError("");
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
        setWaterUnitLabel(settings.waterUnitLabel || "litres");
      }
      const currentHouses = await farmApi.listHouses();
      setHouses(currentHouses);
      setBatchForm(form => ({ ...form, houseId: form.houseId || currentHouses[0]?.id || "" }));
      const currentBatches = await batchApi.list();
      setBatches(currentBatches);
      const lastBatchId = getLastBatchId();
      // A dashboard alert may open the app directly on a contextual hash route.
      // Prefer that valid batch over the remembered/default batch so initial
      // loading cannot silently discard the alert's target.
      const contextBatchId = parseAttentionContext(window.location.hash).batchId;
      const preferredBatchId = currentBatches.some(batch => batch.id === contextBatchId && batch.status === "ACTIVE")
        ? contextBatchId
        : currentBatches.some(batch => batch.id === lastBatchId && batch.status === "ACTIVE")
          ? lastBatchId
          : currentBatches.find(batch => batch.status === "ACTIVE")?.id || currentBatches[0]?.id || "";
      const preferredBatch = currentBatches.find(batch => batch.id === preferredBatchId);
      if (preferredBatchId) {
        rememberLastBatchId(preferredBatchId);
        setSelectedBatchId(preferredBatchId);
      }
      if (preferredBatch?.status === "ACTIVE") {
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
      if (!preserveForms) setAccountForm({ username: currentAccount.username || "", email: currentAccount.email || "", fullName: currentAccount.fullName, currentPassword: "", newPassword: "" });
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
        setDataLoadError(displayApiError(err));
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
      setDataLoadError(displayApiError(err));
      setError(displayApiError(err));
      if (!preserveScreen) setLoading(false);
      return false;
    }
  }
  async function retryData() {
    setDataLoadError("");
    setError("");
    setDailyRecordReloadKey(value => value + 1);
    return initialize({ preserveScreen: true });
  }

  async function signOut() {
    setSaving(true);
    setError("");
    try {
      await authApi.logout();
      setAuthStatus({ authenticated: false, setupRequired: false });
      setAuthForm({ username: "", identifier: "", email: "", fullName: "", password: "", rememberMe: false });
      setShowAuthPassword(false);
      setSettingsReady(false);
    } catch (err) {
      setError(displayApiError(err));
    } finally {
      setSaving(false);
    }
  }

  async function submitAuth(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      // Read the submitted controls directly as well as supporting React state.
      // Browsers can autofill controlled inputs without dispatching the expected
      // React change event, which otherwise sends blank credentials to the API.
      const formData = new FormData(event.currentTarget);
      const submittedUsername = String(formData.get("username") ?? authForm.username).trim();
      const submittedIdentifier = String(formData.get("identifier") ?? authForm.identifier).trim();
      const submittedFullName = String(formData.get("fullName") ?? authForm.fullName).trim();
      const submittedEmail = String(formData.get("email") ?? authForm.email).trim();
      const submittedPassword = String(formData.get("password") ?? authForm.password);

      if (!submittedPassword || (authStatus?.setupRequired ? !submittedUsername : !submittedIdentifier)) {
        setError(authStatus?.setupRequired
          ? "Enter a username and password to create the owner account."
          : "Enter your username/email and password to sign in.");
        return;
      }

      const isSetup = Boolean(authStatus?.setupRequired);
      if (isSetup) {
        await authApi.setup({
          username: submittedUsername,
          fullName: submittedFullName,
          email: submittedEmail || null,
          password: submittedPassword,
        });
      }

      await authApi.login({
        loginIdentifier: isSetup ? submittedUsername : submittedIdentifier,
        password: submittedPassword,
        rememberMe: isSetup ? false : authForm.rememberMe,
      });
      setAuthStatus({ authenticated: true, setupRequired: false });
      await load();
    } catch (err) {
      setError(displayApiError(err));
    } finally {
      setSaving(false);
    }
  }
  async function loadReport(from = dateRange.from, to = dateRange.to) {
    if (!isValidDateRange({ from, to })) return;
    const requestId = ++farmReportRequestSequence.current;
    setReportLoading(true);
    try {
      const report = await reportApi.farm(null, from, to);
      if (requestId === farmReportRequestSequence.current) setFarmReport(report);
    } catch (err) {
      if (requestId === farmReportRequestSequence.current) {
        setDataLoadError(displayApiError(err));
        setError(displayApiError(err));
      }
    } finally {
      if (requestId === farmReportRequestSequence.current) setReportLoading(false);
    }
  }

  async function loadBatchReport(id = selectedBatchId, from = dateRange.from, to = dateRange.to) {
    if (!id || !isValidDateRange({ from, to })) {
      setBatchReport(null);
      setBatchReportLoading(false);
      return;
    }
    const requestId = ++batchReportRequestSequence.current;
    setBatchReportLoading(true);
    try {
      const report = await reportApi.batch(id, null, from, to);
      if (requestId === batchReportRequestSequence.current) setBatchReport(report);
    } catch (err) {
      if (requestId === batchReportRequestSequence.current) {
        setDataLoadError(displayApiError(err));
        setError(displayApiError(err));
      }
    } finally {
      if (requestId === batchReportRequestSequence.current) setBatchReportLoading(false);
    }
  }

  async function exportCsv(loader, filename) {
    setReportExporting(true);
    setError("");
    try {
      const csv = await loader();
      const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      notify.success("Export ready. Download started for " + filename + ".");
    } catch (err) {
      setError(displayApiError(err));
    } finally {
      setReportExporting(false);
    }
  }

  async function exportPdf(loader, filename) {
    setReportExporting(true);
    setError("");
    setSuccessMessage("");
    try {
      const blob = await loader();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      notify.success("Export ready. Download started for " + filename + ".");
    } catch (err) {
      setError(displayApiError(err));
    } finally {
      setReportExporting(false);
    }
  }

  async function loadBatchDashboard(id = selectedBatchId) {
    if (!id) { setBatchDashboard(null); setBatchDashboardLoading(false); return; }
    setBatchDashboardLoading(true);
    try {
      const [summary, adjustments] = await Promise.all([dashboardApi.batch(id), populationApi.adjustments(id)]);
      setBatchDashboard(summary);
      setPopulationAdjustments(adjustments);
    } catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
    finally { setBatchDashboardLoading(false); }
  }

  async function loadDashboard() {
    if (!farm?.timezone) return;
    const today = createDateRange("TODAY", farm.timezone).from;
    if (!isValidDateRange({ from: today, to: today })) return;
    const requestId = ++dashboardRequestSequence.current;
    try {
      const [summary, attentionRows, audit] = await Promise.all([
        dashboardApi.farm(null, today, today),
        attentionApi.list(),
        auditApi.list(20, today, today),
      ]);
      if (requestId !== dashboardRequestSequence.current) return;
      setDashboard(summary);
      setAttention(attentionRows);
      setAuditRows(audit);
    } catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
  }

  useEffect(() => {
    function applyAttentionContext() {
      const context = parseAttentionContext(window.location.hash);
      if (context.batchId) {
        setSelectedBatchId(context.batchId);
        setDailyForm(form => ({ ...form, batchId: context.batchId, ...(context.date ? { date: context.date } : {}) }));
        setVaccinationForm(form => ({ ...form, batchId: context.batchId }));
        setEggBatchId(context.batchId);
        setEggCollectionForm(form => ({ ...form, batchId: context.batchId }));
        setBroilerBatchId(context.batchId);
        setWeightForm(form => ({ ...form, batchId: context.batchId }));
        setBirdSaleForm(form => ({ ...form, batchId: context.batchId }));
      }
      if (context.date) {
        setDailyForm(form => ({ ...form, date: context.date }));
      }
      if (context.feedTypeId) {
        setFeedPurchaseForm(form => ({ ...form, feedTypeId: context.feedTypeId }));
      }
      if (context.itemId) {
        setInventoryMovementForm(form => ({ ...form, itemId: context.itemId }));
      }
    }
    window.addEventListener("hashchange", applyAttentionContext);
    applyAttentionContext();
    return () => window.removeEventListener("hashchange", applyAttentionContext);
  }, []);

  useEffect(() => {
    function applyQuickActionContext() {
      const segments = String(window.location.hash || "").replace(/^#\/?/, "").split("/");
      if (segments[0] !== "daily-work" || segments[1] !== "mortality") return;

      const context = parseAttentionContext(window.location.hash);
      const contextualBatch = batches.find(batch => batch.id === context.batchId && batch.status === "ACTIVE");
      const fallbackBatch = batches.find(batch => batch.id === selectedBatchId && batch.status === "ACTIVE")
        || batches.find(batch => batch.status === "ACTIVE");
      const targetBatchId = contextualBatch?.id || fallbackBatch?.id || "";

      if (targetBatchId) {
        setSelectedBatchId(targetBatchId);
        setMortalityForm(form => ({ ...form, batchId: targetBatchId }));
      }
      setMortalityModalSignal(signal => signal + 1);
    }

    window.addEventListener("hashchange", applyQuickActionContext);
    applyQuickActionContext();
    return () => window.removeEventListener("hashchange", applyQuickActionContext);
  }, [batches, selectedBatchId]);

  useEffect(() => { initialize(); }, []);
  useEffect(() => {
    if (farm) void loadDashboard();
  }, [farm]);

  useEffect(() => {
    if (farm && isValidDateRange(dateRange)) void loadReport(dateRange.from, dateRange.to);
  }, [farm, dateRange.from, dateRange.to]);

  useEffect(() => {
    if (!farm?.timezone || dateRange.preset === "CUSTOM") return;
    const next = createDateRange(dateRange.preset, farm.timezone);
    if (next.from !== dateRange.from || next.to !== dateRange.to) {
      dashboardRequestSequence.current += 1;
      farmReportRequestSequence.current += 1;
      batchReportRequestSequence.current += 1;
      setDashboard(null);
      setFarmReport(null);
      setBatchReport(null);
      setAuditRows([]);
      setDateRange(next);
    }
  }, [farm?.timezone, dateRange.preset, dateRange.from, dateRange.to]);
  useEffect(() => {
    if (!farm) return undefined;

    function refreshAfterMutation(event) {
      const path = event.detail?.path || "";
      const affectsDashboard = /^\/(?:farm(?:\/settings)?|farm\/houses|batches|feed|expenses|inventory|sales|pricing|settings|backup)(?:\/|$)/.test(path);
      if (!affectsDashboard) return;

      void loadDashboard();
      void loadReport();
      if (selectedBatchId && batches.some(batch => batch.id === selectedBatchId)) {
        void loadBatchDashboard(selectedBatchId);
        void loadBatchReport(selectedBatchId);
      }
    }

    window.addEventListener("pomas:data-mutated", refreshAfterMutation);
    return () => window.removeEventListener("pomas:data-mutated", refreshAfterMutation);
  }, [farm, selectedBatchId, batches, dateRange.from, dateRange.to]);
  useEffect(() => {
    if (selectedBatchId && batches.some(batch => batch.id === selectedBatchId)) {
      loadBatchDashboard(selectedBatchId);
    }
  }, [selectedBatchId, batches]);

  useEffect(() => {
    if (selectedBatchId && batches.some(batch => batch.id === selectedBatchId)) {
      loadBatchReport(selectedBatchId, dateRange.from, dateRange.to);
    } else {
      setBatchReport(null);
    }
  }, [selectedBatchId, batches, dateRange.from, dateRange.to]);

  useEffect(() => {
    let current = true;
    const { batchId, date } = dailyForm;
    setFeedUsageForm(form => ({ ...form, batchId, usageDate: date }));
    setDailyRecord(null);
    if (!batchId || !date) {
      setDailyRecordLoading(false);
      return () => { current = false; };
    }
    setDailyRecordLoading(true);
    dailyApi.get(batchId, date)
      .then(record => { if (current) setDailyRecord(record); })
      .catch(err => {
        if (!current) return;
        if (err.status === 404) setDailyRecord(null);
        else { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
      })
      .finally(() => { if (current) setDailyRecordLoading(false); });
    return () => { current = false; };
  }, [dailyForm.batchId, dailyForm.date, dailyRecordReloadKey]);

  async function submitFarm(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const created = await farmApi.create(farmForm);
      setFarm(created);
      const settings = await farmApi.getSettings();
      setCrateSize(settings.defaultCrateSize);
      setDefaultWater(settings.defaultWaterContainerSize ?? "");
      setWaterSizes(settings.waterContainerSizes.join(", "));
      setWaterUnitLabel(settings.waterUnitLabel || "litres");
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
    try { const updated = await accountApi.update(accountForm); setAccount(updated); setAccountForm({ username: updated.username || "", email: updated.email || "", fullName: updated.fullName, currentPassword: "", newPassword: "" }); }
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
      notify.success("Backup completed successfully.");
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function restoreBackup() {
    setSaving(true); setError("");
    try { await window.poultryDesktop?.restoreBackup?.(); await load(); notify.success("Backup restored successfully."); } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function saveSettings(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const sizes = waterSizes.split(",").map(v => Number(v.trim())).filter(Boolean);
      const updated = await farmApi.updateSettings({
        defaultCrateSize:Number(crateSize),
        defaultWaterContainerSize:defaultWater === "" ? null : Number(defaultWater),
        waterContainerSizes:sizes,
        waterUnitLabel:waterUnitLabel.trim()
      });
      setCrateSize(updated.defaultCrateSize);
      setDefaultWater(updated.defaultWaterContainerSize ?? "");
      setWaterSizes(updated.waterContainerSizes.join(", "));
      setWaterUnitLabel(updated.waterUnitLabel || "litres");
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function loadExpenses(batchId = expenseBatchFilter) {
    try {
      setExpenses(await expenseApi.list(batchId || null));
    } catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
  }
  async function loadSales() {
    try { setSales(await salesApi.list()); }
    catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
  }

  async function saveSupplier(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const payload = { name: supplierForm.name.trim(), phone: supplierForm.phone.trim() || null,
        email: supplierForm.email.trim() || null, address: supplierForm.address.trim() || null, notes: supplierForm.notes.trim() || null };
      const saved = editingSupplierId
        ? await supplierApi.update(editingSupplierId, payload)
        : await supplierApi.create(payload);
      setSuppliers(current => (editingSupplierId
        ? current.map(item => item.id === saved.id ? saved : item)
        : [...current, saved]).sort((a, b) => a.name.localeCompare(b.name)));
      setSupplierForm(emptySupplier); setEditingSupplierId("");
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  function editSupplier(supplier) {
    setEditingSupplierId(supplier.id);
    setSupplierForm({ name: supplier.name || "", phone: supplier.phone || "", email: supplier.email || "", address: supplier.address || "", notes: supplier.notes || "" });
  }
  function cancelSupplierEdit() { setEditingSupplierId(""); setSupplierForm(emptySupplier); }

  async function archiveSupplier(id) {
    setSaving(true); setError("");
    try { await supplierApi.archive(id); setSuppliers(current => current.filter(supplier => supplier.id !== id)); }
    catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function saveCustomer(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const payload = { name: customerForm.name.trim(), phone: customerForm.phone.trim() || null,
        notes: customerForm.notes.trim() || null };
      const saved = editingCustomerId
        ? await customerApi.update(editingCustomerId, payload)
        : await customerApi.create(payload);
      setCustomers(current => (editingCustomerId
        ? current.map(item => item.id === saved.id ? saved : item)
        : [...current, saved]).sort((a, b) => a.name.localeCompare(b.name)));
      await loadSales();
      setCustomerForm(emptyCustomer); setEditingCustomerId("");
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  function editCustomer(customer) {
    setEditingCustomerId(customer.id);
    setCustomerForm({ name: customer.name || "", phone: customer.phone || "", notes: customer.notes || "" });
  }
  function cancelCustomerEdit() { setEditingCustomerId(""); setCustomerForm(emptyCustomer); }


  async function saveInventoryItem(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const payload = { name: inventoryItemForm.name.trim(), category: inventoryItemForm.category,
        unit: inventoryItemForm.unit.trim(), reorderLevel: inventoryItemForm.reorderLevel === "" ? 0 : Number(inventoryItemForm.reorderLevel) };
      const saved = editingInventoryItemId
        ? await inventoryApi.updateItem(editingInventoryItemId, payload)
        : await inventoryApi.createItem(payload);
      setInventoryItems(current => (editingInventoryItemId
        ? current.map(item => item.id === saved.id ? saved : item)
        : [...current, saved]).sort((a, b) => a.name.localeCompare(b.name)));
      setInventoryItemForm(emptyInventoryItem); setEditingInventoryItemId("");
      if (!editingInventoryItemId) setInventoryMovementForm(form => ({ ...form, itemId: saved.id }));
      await loadDashboard();
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  function editInventoryItem(item) {
    setEditingInventoryItemId(item.id);
    setInventoryItemForm({ name: item.name, category: item.category, unit: item.unit, reorderLevel: String(item.reorderLevel ?? 0) });
  }
  function cancelInventoryItemEdit() { setEditingInventoryItemId(""); setInventoryItemForm(emptyInventoryItem); }

  async function loadInventoryMovements(itemId = inventoryMovementForm.itemId) {
    if (!itemId) { setInventoryMovements([]); return; }
    try { setInventoryMovements(await inventoryApi.listMovements(itemId)); }
    catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
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
      await discardDraft("expense", false);
      setExpenses(current => [created, ...current]);
      setExpenseForm(form => ({ ...emptyExpense, batchId: form.batchId }));
      notify.success("Expense recorded successfully.");
      await Promise.all([loadDashboard(), loadBatchDashboard(expenseForm.batchId || selectedBatchId)]);
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
        purchaseCostRecorded: batchForm.purchaseCostRecorded,
        purchaseCostMinor: batchForm.purchaseCostRecorded
          ? parseNairaToKobo(batchForm.purchaseCostMinor, { allowZero: true })
          : null,
        populationCountStatus: batchForm.populationCountStatus || "ESTIMATED",
      });
      await discardDraft("batch", false);
      setBatches(current => [created, ...current]);
      rememberLastBatchId(created.id);
      setSelectedBatchId(created.id);
      setBatchForm({ ...emptyBatch, houseId: batchForm.houseId });
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function markPopulationEstimated(batchId) {
    const reason = window.prompt("Why is this batch count provisional?");
    if (!reason || !reason.trim()) return;
    setSaving(true); setError("");
    try {
      await populationApi.markEstimated(batchId, reason.trim());
      notify.success("Batch population marked as estimated.");
      await Promise.all([loadBatchDashboard(batchId), loadDashboard()]);
      setBatches(await batchApi.list());
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  async function savePopulationAdjustment(event) {
    event.preventDefault();
    if (!selectedBatchId) return;
    setSaving(true); setError("");
    try {
      await populationApi.adjust(selectedBatchId, {
        effectiveDate: populationAdjustmentForm.effectiveDate,
        newCount: Number(populationAdjustmentForm.newCount),
        reason: populationAdjustmentForm.reason.trim(),
      });
      setPopulationAdjustmentForm(emptyPopulationAdjustment);
      notify.success("Physical bird count recorded. The batch count is now verified.");
      await Promise.all([loadBatchDashboard(selectedBatchId), loadDashboard()]);
      const refreshed = await batchApi.list();
      setBatches(refreshed);
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
    event.preventDefault();
    if (!feedTypeForm.applicableTypes?.length) {
      setError("Select at least one applicable bird type.");
      return;
    }
    setSaving(true); setError("");
    try {
      const created = await feedApi.createType({ ...feedTypeForm, kgPerUnit: feedTypeForm.kgPerUnit === "" ? null : Number(feedTypeForm.kgPerUnit), packageWeightKg: feedTypeForm.packageWeightKg === "" ? null : Number(feedTypeForm.packageWeightKg) });
      setFeedTypes(current => [...current, created].sort((a, b) => a.name.localeCompare(b.name) || (a.brand || "").localeCompare(b.brand || "")));
      setFeedTypeForm(emptyFeedType);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  function editFeedType(type) {
    setEditingFeedTypeId(type.id);
    setFeedTypeForm({ name: type.name || "", brand: type.brand || "", unit: type.unit || "bag",
      packagingName: type.packagingName || "", packageWeightKg: type.packageWeightKg == null ? "" : String(type.packageWeightKg),
      applicableType: type.applicableType || "BOTH", kgPerUnit: type.kgPerUnit == null ? "" : String(type.kgPerUnit) });
  }
  function cancelFeedTypeEdit() { setEditingFeedTypeId(""); setFeedTypeForm(emptyFeedType); }

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
      await discardDraft("feedPurchase", false);
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
      setFeedUsageForm(form => ({ ...emptyFeedUsage, batchId: form.batchId, feedTypeId: form.feedTypeId, usageDate: form.usageDate }));
      setFeedInventory(await feedApi.inventory());
      if (dailyForm.batchId === feedUsageForm.batchId && dailyForm.date === feedUsageForm.usageDate) {
        try {
          setDailyRecord(await dailyApi.get(feedUsageForm.batchId, feedUsageForm.usageDate));
        } catch (err) {
          if (err.status === 404) setDailyRecord(null);
          else throw err;
        }
      }
      notify.success("Feed usage recorded successfully.");
      await Promise.all([loadDashboard(), loadBatchDashboard(feedUsageForm.batchId)]);
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

  async function saveDailyRecord(event, mode = "all") {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const payload = {
        recordDate: dailyForm.date,
        // Omitted fields are preserved by the backend. Each form only writes fields it owns.
        notes: mode === "water" ? null : (dailyForm.notes.trim() || null),
        waterContainers: mode === "notes" ? null : parseWaterEntries(dailyForm.water),
      };
      const saved = await dailyApi.create(dailyForm.batchId, payload);
      setDailyRecord(saved);
      setDailyForm(form => ({ ...form, notes: saved.notes || "", water: saved.water.map(entry => `${entry.capacityUnits}:${entry.containerCount}`).join(", ") }));
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
      notify.success(type === "MORTALITY" ? "Mortality recorded successfully." : "Culling recorded successfully.");
      await Promise.all([loadDashboard(), loadBatchDashboard(form.batchId)]);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }


  async function loadPricingForBatch(batchId, quantity, date) {
    if (!batchId || !quantity) { setPricing(null); return; }
    try { setPricing(await pricingApi.price(batchId, Number(quantity), date || undefined)); }
    catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
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
    } catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
  }

  async function saveWeight(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const batchId = weightForm.batchId;
      const saved = await broilerApi.addWeight(batchId, {
        recordDate: weightForm.date,
        sampleQuantity: Number(weightForm.sampleQuantity),
        totalWeightKg: weightForm.totalWeightKg,
        notes: weightForm.notes.trim() || null,
      });
      await discardDraft("weight", false);
      await loadBroilerForBatch(batchId);
      notify.success("Weight record saved.", {
        key: "weight-undo:" + saved.id,
        duration: 15000,
        action: {
          label: "Undo",
          onClick: async () => {
            const reason = window.prompt("Why are you undoing this weight record? A reason is required.");
            if (reason === null) return;
            try {
              await broilerApi.undoWeight(batchId, saved.id, reason);
              await loadBroilerForBatch(batchId);
              notify.success("Weight record reversed. Growth calculations have been updated.");
            } catch (undoError) { notify.error(displayApiError(undoError), { duration: 0 }); }
          },
        },
      });
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
      await discardDraft("birdSale", false);
      await loadBroilerForBatch(batchId);
      setBatches(await batchApi.list());
      setBirdSaleForm(form => ({ ...form, quantity: "", pricePerBirdMinor: "", customer: "", customerId: "" }));
      await loadSales();
      notify.success("Bird sale recorded successfully.");
      await Promise.all([loadDashboard(), loadBatchDashboard(batchId)]);
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
    } catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
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
      await discardDraft("eggCollection", false);
      await loadEggsForBatch(batchId);
      setEggCollectionForm(form => ({ ...form, good: "", cracked: "", notes: "" }));
      notify.success("Egg collection recorded successfully.");
      await Promise.all([loadDashboard(), loadBatchDashboard(batchId)]);
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
      await discardDraft("eggSale", false);
      await loadEggsForBatch(batchId);
      setEggSaleForm(form => ({ ...form, customer: "", customerId: "", crates: "", pricePerCrateMinor: "" }));
      await loadSales();
      notify.success("Egg sale recorded successfully.");
      await Promise.all([loadDashboard(), loadBatchDashboard(batchId)]);
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
      await discardDraft("health", false);
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
      await discardDraft("drug", false);
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
      await discardDraft("vaccination", false);
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
    } catch (err) { setDataLoadError(displayApiError(err)); setError(displayApiError(err)); }
  }

  function editBatchSetup(batch) {
    setEditingBatchId(batch.id);
    setBatchSetupForm({ houseId: batch.houseId || "", supplierId: batch.supplierId || "" });
  }

  function cancelBatchSetup() {
    setEditingBatchId("");
    setBatchSetupForm({ houseId: "", supplierId: "" });
  }

  async function saveBatchSetup(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const updated = await batchApi.updateSetup(editingBatchId, {
        houseId: batchSetupForm.houseId,
        supplierId: batchSetupForm.supplierId || null,
      });
      setBatches(current => current.map(batch => batch.id === updated.id ? updated : batch));
      cancelBatchSetup();
      await loadDashboard();
      if (selectedBatchId === updated.id) await loadBatchDashboard(updated.id);
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
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

  async function saveHouse(event) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      if (editingHouseId) {
        const current = houses.find(house => house.id === editingHouseId);
        await farmApi.updateHouse(editingHouseId, { ...houseForm, status: current?.status || "ACTIVE" });
      } else {
        await farmApi.createHouse(houseForm);
      }
      setHouseForm(emptyHouse);
      setEditingHouseId("");
      setHouses(await farmApi.listHouses());
      await loadDashboard();
    } catch (err) { setError(displayApiError(err)); } finally { setSaving(false); }
  }

  function editHouse(house) {
    setEditingHouseId(house.id);
    setHouseForm({ name: house.name, code: house.code, notes: house.notes || "", houseType: house.houseType });
  }

  function cancelHouseEdit() {
    setEditingHouseId("");
    setHouseForm(emptyHouse);
  }

  if (loading) return (
    <main className="pomas-loading-screen" aria-label="Pomas is starting">
      <section className="pomas-loading-content" aria-labelledby="pomas-loading-title">
        <img className="pomas-loading-logo" src={pomasLogo} alt="Pomas logo" />
        <h1 id="pomas-loading-title">Pomas</h1>
        <p className="pomas-loading-description">Poultry management, made clear.</p>
        <p className="pomas-loading-detail">Preparing your farm workspace and records.</p>
        <div className="pomas-loading-spinner" role="status" aria-label="Loading" />
      </section>
    </main>
  );

  if (!authStatus?.authenticated) return (
    <main className="auth-screen">
      <section className="auth-story" aria-labelledby="auth-story-title">
        <header className="auth-brand">
          <img className="auth-brand-mark" src={pomasLogo} alt="Pomas logo" />
          <span><strong>Pomas</strong><small>POULTRY FARM MANAGEMENT</small></span>
        </header>

        <div className="auth-story-content">
          <p className="auth-kicker"><span className="auth-kicker-dot" /> POULTRY FARM MANAGEMENT</p>
          <h1 id="auth-story-title">Run your farm with <span>clarity.</span></h1>
          <p className="auth-story-intro">
            Keep your flock records, feed costs, production, and farm finances in one dependable workspace.
            Built for the daily reality of running a layer or broiler farm.
          </p>

          <div className="auth-feature-list">
            <article className="auth-feature">
              <span className="auth-feature-icon" aria-hidden="true">01</span>
              <div><h2>Know your flock</h2><p>Track batches, bird counts, mortality, health, and daily farm activity.</p></div>
            </article>
            <article className="auth-feature">
              <span className="auth-feature-icon" aria-hidden="true">02</span>
              <div><h2>Follow the cost</h2><p>Record feed purchases, FIFO usage, expenses, and sales without double-counting costs.</p></div>
            </article>
            <article className="auth-feature">
              <span className="auth-feature-icon" aria-hidden="true">03</span>
              <div><h2>See the full picture</h2><p>Review egg production, broiler growth, stock, and farm or batch reports.</p></div>
            </article>
          </div>

          <section className="auth-whats-new" aria-labelledby="auth-whats-new-title">
            <div className="auth-whats-new-heading">
              <p className="auth-whats-new-kicker">{appVersionLabel(appInfo)}</p>
              <h2 id="auth-whats-new-title">What's new</h2>
              <p>Reach common farm tasks faster, with one Quick actions menu and keyboard shortcuts across the workspace.</p>
            </div>
            <div className="auth-whats-new-grid">
              <article className="auth-whats-new-item">
                <span>01</span><div><h3>Quick actions in one place</h3><p>Start common daily, production, finance, and stock workflows from the top bar.</p></div>
              </article>
              <article className="auth-whats-new-item">
                <span>02</span><div><h3>Keyboard-first navigation</h3><p>Use Ctrl+1 through Ctrl+0 to navigate, Ctrl+K for Quick actions, and Ctrl+F to focus page search when available.</p></div>
              </article>
              <article className="auth-whats-new-item">
                <span>03</span><div><h3>A cleaner dashboard</h3><p>Dashboard space is reserved for farm performance, attention items, and recent activity.</p></div>
              </article>
            </div>
          </section>

          <div className="auth-local-note">
            <span className="auth-local-symbol" aria-hidden="true">⌂</span>
            <div><strong>Your records stay local.</strong><p>Pomas runs on this computer and is designed to work without an internet connection.</p></div>
          </div>
        </div>

        <footer className="auth-story-footer">
          <span>BUILT FOR THE WORK THAT HAPPENS ON THE FARM</span>
          <span>LOCAL-FIRST · OFFLINE-READY</span>
        </footer>
        <div className="auth-orbit auth-orbit-one" aria-hidden="true" />
        <div className="auth-orbit auth-orbit-two" aria-hidden="true" />
      </section>

      <section className="auth-panel" aria-labelledby="auth-form-title">
        <div className="auth-panel-topline">
          <span className="auth-panel-access"><span className="auth-lock" aria-hidden="true">▣</span>LOCAL WORKSPACE ACCESS</span>
          <ThemePreferenceSwitches value={themePreference} onChange={changeThemePreference} className="auth-theme-control" showSystem={false} />
        </div>
        <div className="auth-card">
          <div className="auth-mobile-brand"><img className="auth-brand-mark" src={pomasLogo} alt="Pomas logo" /><strong>Pomas</strong></div>
          <div className="auth-form-heading">
            <p className="auth-form-eyebrow">{authStatus?.setupRequired ? "LET'S GET YOUR FARM STARTED" : "WELCOME BACK"}</p>
            <h2 id="auth-form-title">{authStatus?.setupRequired ? "Create your owner account" : "Sign in to Pomas"}</h2>
            <p>{authStatus?.setupRequired
              ? "Set up the primary account that protects access to your farm records."
              : "Pick up where you left off. Your farm workspace is right here."}</p>
          </div>

          {error && <div className="error auth-error" role="alert">{error}</div>}
          {authStatus ? (
            <form className="auth-form" onSubmit={submitAuth}>
              {authStatus.setupRequired ? (
                <>
                  <label className="auth-field" htmlFor="auth-full-name">Full name
                    <input id="auth-full-name" name="fullName" required maxLength="120" autoComplete="name" placeholder="e.g. Noble Ekwere" value={authForm.fullName} onChange={e => setAuthForm({...authForm, fullName:e.target.value})}/>
                  </label>
                  <label className="auth-field" htmlFor="auth-username">Username
                    <input id="auth-username" name="username" required minLength="3" maxLength="32" autoComplete="username" pattern="[A-Za-z0-9][A-Za-z0-9._\-]{2,31}" placeholder="Choose a username" value={authForm.username} onChange={e => setAuthForm({...authForm, username:e.target.value})}/>
                    <small>3–32 characters. Letters, numbers, dots, underscores, and hyphens.</small>
                  </label>
                  <label className="auth-field" htmlFor="auth-email">Email <span className="auth-optional">(optional)</span>
                    <input id="auth-email" name="email" type="email" autoComplete="email" placeholder="you@example.com" value={authForm.email} onChange={e => setAuthForm({...authForm, email:e.target.value})}/>
                  </label>
                </>
              ) : (
                <label className="auth-field" htmlFor="auth-identifier">Username or email
                  <input id="auth-identifier" name="identifier" required autoComplete="username" placeholder="Enter your username or email" value={authForm.identifier} onChange={e => setAuthForm({...authForm, identifier:e.target.value})}/>
                </label>
              )}
              <label className="auth-field" htmlFor="auth-password">Password
                <span className="auth-password-wrap">
                  <input id="auth-password" name="password" required type={showAuthPassword ? "text" : "password"} minLength={authStatus.setupRequired ? 10 : undefined} autoComplete={authStatus.setupRequired ? "new-password" : "current-password"} placeholder={authStatus.setupRequired ? "Create a strong password" : "Enter your password"} value={authForm.password} onChange={e => setAuthForm({...authForm, password:e.target.value})}/>
                  <button className="auth-password-toggle" type="button" onClick={() => setShowAuthPassword(value => !value)} aria-label={showAuthPassword ? "Hide password" : "Show password"}>{showAuthPassword ? "Hide" : "Show"}</button>
                </span>
                {authStatus.setupRequired && <small>Use at least 10 characters.</small>}
              </label>
              {!authStatus.setupRequired && (
                <label className="auth-remember">
                  <input type="checkbox" checked={authForm.rememberMe} onChange={e => setAuthForm({...authForm, rememberMe:e.target.checked})}/>
                  <span><strong>Keep me signed in</strong><small>Only enable this on a computer you trust.</small></span>
                </label>
              )}
              <button className="auth-submit" disabled={saving} type="submit">
                <span>{saving ? (authStatus.setupRequired ? "Creating account..." : "Signing in...") : (authStatus.setupRequired ? "Create account and continue" : "Sign in to your farm")}</span>
                <span aria-hidden="true">↗</span>
              </button>
              <p className="auth-form-footnote">{authStatus.setupRequired
                ? "This creates the primary local owner account. Multi-user access is not required to get started."
                : "Access is managed locally by your Pomas installation."}</p>
            </form>
          ) : (
            <div className="auth-connect-state">
              <p>Could not confirm the local account status.</p>
              <button className="auth-submit" type="button" onClick={initialize}>Retry connection <span aria-hidden="true">↻</span></button>
            </div>
          )}
        </div>
        <footer className="auth-panel-footer"><span>{appVersionLabel(appInfo)}</span><span>YOUR FARM. YOUR RECORDS.</span></footer>
      </section>
    </main>
  );
  if (!farm) return (
    <main className="shell narrow">
      <header className="hero"><p className="eyebrow">POMAS · FARM MANAGEMENT</p><h1>Set up your farm</h1><p className="muted">Create the local farm profile first. Everything else hangs from it.</p></header>
      {error && <div className="error">{error}</div>}
      <ValidatedForm className="card form-grid" onSubmit={submitFarm}>
        <label>Farm name<input required value={farmForm.name} onChange={e=>setFarmForm({...farmForm,name:e.target.value})}/></label>
        <label>Location<input value={farmForm.location} onChange={e=>setFarmForm({...farmForm,location:e.target.value})}/></label>
        <label>Timezone<input required value={farmForm.timezone} onChange={e=>setFarmForm({...farmForm,timezone:e.target.value})}/></label>
        <label>Currency<input required maxLength="3" value={farmForm.currency} onChange={e=>setFarmForm({...farmForm,currency:e.target.value.toUpperCase()})}/></label>
        <button disabled={saving} type="submit">{saving ? "Creating..." : "Create farm"}</button>
      </ValidatedForm>
    </main>
  );

  function handleQuickAction(actionId) {
    const batch = batches.find(item => item.id === selectedBatchId && item.status === "ACTIVE")
      || batches.find(item => item.status === "ACTIVE");
    const today = todayLocalDate();

    switch (actionId) {
      case "mortality":
        if (!batch) return;
        setMortalityForm(form => ({ ...form, batchId: batch.id, date: today }));
        setMortalityModalSignal(signal => signal + 1);
        break;
      case "egg-collection": {
        const layer = batch?.type === "LAYER"
          ? batch
          : batches.find(item => item.type === "LAYER" && item.status === "ACTIVE");
        if (!layer) return;
        setEggBatchId(layer.id);
        setEggCollectionForm(form => ({ ...form, batchId: layer.id, date: today }));
        loadEggsForBatch(layer.id);
        break;
      }
      case "feed-usage": {
        if (!batch) return;
        const feedType = feedTypes.find(type => type.status === "ACTIVE"
          && (type.applicableTypes || []).includes(batch.type)
          && feedInventory.some(item => item.feedTypeId === type.id && Number(item.remainingQuantityMilli) > 0));
        setDailyForm(form => ({ ...form, batchId: batch.id, date: today }));
        setFeedUsageForm(form => ({
          ...form,
          batchId: batch.id,
          feedTypeId: feedType?.id || "",
          unit: feedType?.unit || form.unit,
          usageDate: today,
        }));
        setFeedUsageModalSignal(signal => signal + 1);
        break;
      }
      case "feed-purchase": {
        const feedType = feedTypes.find(type => type.status === "ACTIVE");
        setFeedPurchaseForm(form => ({
          ...form,
          feedTypeId: feedType?.id || form.feedTypeId,
          unit: feedType?.unit || form.unit,
          purchaseDate: today,
        }));
        setFeedPurchaseModalSignal(signal => signal + 1);
        break;
      }
      case "sale":
        if (batch?.type === "LAYER") {
          setEggBatchId(batch.id);
          setEggSaleForm(form => ({ ...form, batchId: batch.id, date: today }));
          loadEggsForBatch(batch.id);
        } else if (batch && (batch.type === "BROILER" || batch.type === "TURKEY")) {
          setBroilerBatchId(batch.id);
          setBirdSaleForm(form => ({ ...form, batchId: batch.id, date: today }));
          loadBroilerForBatch(batch.id);
        }
        break;
      case "expense":
        setExpenseForm(form => ({ ...form, batchId: batch?.id || "", occurredDate: today }));
        break;
      default:
        break;
    }
  }

  const pageModel = { draftStatuses, discardDraft, markDraftReviewed, reportExporting,  signOut, themePreference, dataLoadError, retryData, dateRange, setDateRange: updateDateRange, batchDashboardLoading, reportLoading, batchReportLoading, changeThemePreference, dailyRecordLoading, suppliers, supplierForm, setSupplierForm, saveSupplier, archiveSupplier, editingSupplierId, editSupplier, cancelSupplierEdit, editingCustomerId, editCustomer, cancelCustomerEdit, editingInventoryItemId, editInventoryItem, cancelInventoryItemEdit, editingFeedTypeId, editFeedType, cancelFeedTypeEdit, editingBatchId, setEditingBatchId, batchSetupForm, setBatchSetupForm, saveBatchSetup, editBatchSetup, cancelBatchSetup, formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, displayApiError, notify, emptyWeight, emptyBirdSale, emptyPricing, emptyFarm, emptyHouse, emptyBatch, emptyCost, emptyFeedType, emptyFeedPurchase, emptyFeedUsage, emptyDaily, emptyPopulationEvent, emptyHealth, emptyDrug, emptyVaccination, emptyExpense, emptyCustomer, emptyInventoryItem, emptyInventoryMovement, farm, setFarm, farmForm, setFarmForm, houses, setHouses, batches, setBatches, batchForm, setBatchForm, costForm, setCostForm, feedTypes, setFeedTypes, feedInventory, setFeedInventory, feedTypeForm, setFeedTypeForm, feedPurchaseForm, setFeedPurchaseForm, feedUsageForm, setFeedUsageForm, dailyForm, setDailyForm, dailyRecord, setDailyRecord, mortalityForm, setMortalityForm, mortalityModalSignal, setMortalityModalSignal, feedUsageModalSignal, setFeedUsageModalSignal, feedPurchaseModalSignal, setFeedPurchaseModalSignal, cullingForm, setCullingForm, healthForm, setHealthForm, drugForm, setDrugForm, vaccinationForm, setVaccinationForm, healthRecords, setHealthRecords, drugRecords, setDrugRecords, vaccinationRecords, setVaccinationRecords, expenseForm, setExpenseForm, expenses, setExpenses, expenseBatchFilter, setExpenseBatchFilter, inventoryItems, setInventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, setInventoryMovements, customers, setCustomers, customerForm, setCustomerForm, sales, setSales, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, setBroilerGrowth, broilerWeights, setBroilerWeights, broilerSales, setBroilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, pricingSettings, setPricingSettings, pricing, setPricing, pricingForm, setPricingForm, eggInventory, setEggInventory, eggCollections, setEggCollections, eggSales, setEggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, houseForm, setHouseForm, crateSize, setCrateSize, defaultWater, setDefaultWater, waterSizes, setWaterSizes, waterUnitLabel, setWaterUnitLabel, loading, setLoading, saving, setSaving, error, setError, dashboard, setDashboard, attention, setAttention, auditRows, setAuditRows, expenseCategories, setExpenseCategories, expenseCategoryForm, setExpenseCategoryForm, account, setAccount, accountForm, setAccountForm, applicationSettings, setApplicationSettings, backupSettings, setBackupSettings, selectedBatchId, setSelectedBatchId, batchDashboard, setBatchDashboard, farmReport, setFarmReport, batchReport, setBatchReport, reportAsOf, setReportAsOf, load, loadReport, loadBatchReport, exportCsv, exportPdf, loadBatchDashboard, loadDashboard, submitFarm, saveFarm, createExpenseCategory, archiveExpenseCategory, saveAccount, saveApplicationSettings, chooseBackupSettingsDirectory, saveBackupSettings, createBackup, restoreBackup, saveSettings, loadExpenses, loadSales, saveCustomer, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, saveExpense, createBatch, addBirdCost, purchaseFeed, useFeed, feedQuantity, parseWaterEntries, saveDailyRecord, recordPopulationEvent, loadPricingForBatch, savePricingSettings, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale, saveHealth, saveDrug, saveVaccination, loadHealthForBatch, markBatchSold, reopenBatch, addHouse: saveHouse, editHouse, editingHouseId, cancelHouseEdit, populationAdjustmentForm, setPopulationAdjustmentForm, populationAdjustments, savePopulationAdjustment, markPopulationEstimated };

  return (
    <AppShell farm={farm} error={error} appInfo={appInfo} onRetry={retryData} onQuickAction={handleQuickAction} batches={batches} selectedBatchId={selectedBatchId} feedTypes={feedTypes} feedInventory={feedInventory} inventoryItems={inventoryItems} attention={attention} startPage={applicationSettings.startPage} startPageReady={settingsReady}>
      <FarmManagerContext.Provider value={{ ...pageModel, createFeedType }}>
        <PageSection page="dashboard"><DashboardPage /></PageSection>
        <PageSection page="flocks"><FlocksPage /></PageSection>
        <PageSection page="daily-work"><DailyWorkPage /></PageSection>
        <PageSection page="feed-stock"><FeedStockPage /></PageSection>
        <PageSection page="health"><HealthPage /></PageSection>
        <PageSection page="production"><ProductionPage /></PageSection>
        <PageSection page="sales-money"><SalesMoneyPage /></PageSection>
        <PageSection page="reports"><ReportsPage /></PageSection>
        <PageSection page="settings"><SettingsPage /></PageSection>
        <PageSection page="import-export"><ImportExportPage /></PageSection>
      </FarmManagerContext.Provider>
    </AppShell>
  );
}
