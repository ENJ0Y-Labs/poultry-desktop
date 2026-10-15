import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import PageSection from "../components/PageSection.jsx";

export default function DashboardPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, displayApiError, emptyWeight, emptyBirdSale, emptyPricing, emptyFarm, emptyHouse, emptyBatch, emptyCost, emptyFeedType, emptyFeedPurchase, emptyFeedUsage, emptyDaily, emptyPopulationEvent, emptyHealth, emptyDrug, emptyVaccination, emptyExpense, emptyCustomer, emptyInventoryItem, emptyInventoryMovement, farm, setFarm, farmForm, setFarmForm, houses, setHouses, batches, setBatches, batchForm, setBatchForm, costForm, setCostForm, feedTypes, setFeedTypes, feedInventory, setFeedInventory, feedTypeForm, setFeedTypeForm, feedPurchaseForm, setFeedPurchaseForm, feedUsageForm, setFeedUsageForm, dailyForm, setDailyForm, dailyRecord, setDailyRecord, mortalityForm, setMortalityForm, cullingForm, setCullingForm, healthForm, setHealthForm, drugForm, setDrugForm, vaccinationForm, setVaccinationForm, healthRecords, setHealthRecords, drugRecords, setDrugRecords, vaccinationRecords, setVaccinationRecords, expenseForm, setExpenseForm, expenses, setExpenses, expenseBatchFilter, setExpenseBatchFilter, inventoryItems, setInventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, setInventoryMovements, customers, setCustomers, customerForm, setCustomerForm, sales, setSales, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, setBroilerGrowth, broilerWeights, setBroilerWeights, broilerSales, setBroilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, pricingSettings, setPricingSettings, pricing, setPricing, pricingForm, setPricingForm, eggInventory, setEggInventory, eggCollections, setEggCollections, eggSales, setEggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, houseForm, setHouseForm, crateSize, setCrateSize, defaultWater, setDefaultWater, waterSizes, setWaterSizes, loading, setLoading, saving, setSaving, error, setError, dashboard, setDashboard, attention, setAttention, auditRows, setAuditRows, expenseCategories, setExpenseCategories, expenseCategoryForm, setExpenseCategoryForm, account, setAccount, accountForm, setAccountForm, applicationSettings, setApplicationSettings, backupSettings, setBackupSettings, selectedBatchId, setSelectedBatchId, batchDashboard, setBatchDashboard, farmReport, setFarmReport, batchReport, setBatchReport, load, loadReport, loadBatchReport, exportCsv, exportPdf, loadBatchDashboard, loadDashboard, submitFarm, saveFarm, createExpenseCategory, archiveExpenseCategory, saveAccount, saveApplicationSettings, chooseBackupSettingsDirectory, saveBackupSettings, createBackup, restoreBackup, saveSettings, loadExpenses, loadSales, saveCustomer, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, saveExpense, createBatch, addBirdCost, createFeedType, purchaseFeed, useFeed, feedQuantity, parseWaterEntries, saveDailyRecord, recordPopulationEvent, loadPricingForBatch, savePricingSettings, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale, saveHealth, saveDrug, saveVaccination, loadHealthForBatch, markBatchSold, reopenBatch, addHouse } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="dashboard" tab="overview"><section id="dashboard" className="card full dashboard-page">
        <div className="section-head">
          <div><p className="eyebrow">OVERVIEW</p><h2>Farm dashboard</h2></div>
          <span className="muted">{dashboard?.asOf || "Loading..."}</span>
        </div>
        {dashboard ? (
          <>
            <div className="metric-grid">
              {[
                ["Total birds", formatCount(dashboard.totalBirds), ""], ["Layer birds", formatCount(dashboard.layerBirds), ""],
                ["Broiler birds", formatCount(dashboard.broilerBirds), ""], ["Active batches", formatCount(dashboard.activeBatches), ""],
                ["Mortality", formatCount(dashboard.mortality), "birds"], ["Good eggs", formatCount(dashboard.eggsGood), "eggs"],
                ["Revenue", formatMoney(dashboard.revenueMinor, farm.currency), ""],
                ["Expenses", formatMoney(dashboard.expensesMinor, farm.currency), ""],
                ["Profit", formatMoney(dashboard.profitMinor, farm.currency), ""],
                ["Inventory alerts", dashboard.inventoryAlerts, ""]
              ].map(([label,value,unit]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong>{unit && <small>{unit}</small>}</div>)}
            </div>
            <div className="dashboard-columns">
              <div className="subsection"><h3>Feed</h3><div className="dashboard-list">
                <div><span>Feed types</span><strong>{dashboard.feedSummary?.typeCount ?? 0}</strong></div>
                <div><span>Stock remaining</span><strong>{formatQuantity(dashboard.feedSummary?.remainingQuantityMilli)}</strong></div>
                <div><span>Stock value</span><strong>{formatMoney(dashboard.feedSummary?.remainingCostMinor, farm.currency)}</strong></div>
                <div><span>Consumed cost</span><strong>{formatMoney(dashboard.feedSummary?.consumedCostMinor, farm.currency)}</strong></div>
              </div></div>
              <div className="subsection"><h3>Attention</h3>
                {attention.length === 0 ? <p className="muted">Nothing currently requires attention.</p> :
                  <div className="attention-list">{attention.map((item,index) => <div className="attention-item" key={item.type + index}><span className={"severity " + String(item.severity).toLowerCase()}>{item.severity}</span><div><strong>{item.title}</strong><p>{item.message}</p></div></div>)}</div>}
              </div>
            </div>
            <div className="subsection"><h3>Recent audit activity</h3>{auditRows.length === 0 ? <p className="muted">No audit records yet.</p> :
              <div className="table"><div className="row header"><span>Time</span><span>Action</span><span>Entity</span><span>Reason</span></div>
              {auditRows.slice(0, 8).map(row => <div className="row" key={row.id}><span>{row.occurredAt}</span><span>{row.action}</span><span>{row.entityType}</span><span>{row.reason || "—"}</span></div>)}</div>}</div>
          </>
        ) : <p className="muted">Dashboard is loading…</p>}
      </section></PageSection>
    </div>
  );
}
