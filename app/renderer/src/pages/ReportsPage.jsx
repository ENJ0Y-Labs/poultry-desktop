import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import PageSection from "../components/PageSection.jsx";

export default function ReportsPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, displayApiError, emptyWeight, emptyBirdSale, emptyPricing, emptyFarm, emptyHouse, emptyBatch, emptyCost, emptyFeedType, emptyFeedPurchase, emptyFeedUsage, emptyDaily, emptyPopulationEvent, emptyHealth, emptyDrug, emptyVaccination, emptyExpense, emptyCustomer, emptyInventoryItem, emptyInventoryMovement, farm, setFarm, farmForm, setFarmForm, houses, setHouses, batches, setBatches, batchForm, setBatchForm, costForm, setCostForm, feedTypes, setFeedTypes, feedInventory, setFeedInventory, feedTypeForm, setFeedTypeForm, feedPurchaseForm, setFeedPurchaseForm, feedUsageForm, setFeedUsageForm, dailyForm, setDailyForm, dailyRecord, setDailyRecord, mortalityForm, setMortalityForm, cullingForm, setCullingForm, healthForm, setHealthForm, drugForm, setDrugForm, vaccinationForm, setVaccinationForm, healthRecords, setHealthRecords, drugRecords, setDrugRecords, vaccinationRecords, setVaccinationRecords, expenseForm, setExpenseForm, expenses, setExpenses, expenseBatchFilter, setExpenseBatchFilter, inventoryItems, setInventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, setInventoryMovements, customers, setCustomers, customerForm, setCustomerForm, sales, setSales, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, setBroilerGrowth, broilerWeights, setBroilerWeights, broilerSales, setBroilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, pricingSettings, setPricingSettings, pricing, setPricing, pricingForm, setPricingForm, eggInventory, setEggInventory, eggCollections, setEggCollections, eggSales, setEggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, houseForm, setHouseForm, crateSize, setCrateSize, defaultWater, setDefaultWater, waterSizes, setWaterSizes, loading, setLoading, saving, setSaving, error, setError, dashboard, setDashboard, attention, setAttention, auditRows, setAuditRows, expenseCategories, setExpenseCategories, expenseCategoryForm, setExpenseCategoryForm, account, setAccount, accountForm, setAccountForm, applicationSettings, setApplicationSettings, backupSettings, setBackupSettings, selectedBatchId, setSelectedBatchId, batchDashboard, setBatchDashboard, farmReport, setFarmReport, batchReport, setBatchReport, load, loadReport, loadBatchReport, exportCsv, exportPdf, loadBatchDashboard, loadDashboard, submitFarm, saveFarm, createExpenseCategory, archiveExpenseCategory, saveAccount, saveApplicationSettings, chooseBackupSettingsDirectory, saveBackupSettings, createBackup, restoreBackup, saveSettings, loadExpenses, loadSales, saveCustomer, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, saveExpense, createBatch, addBirdCost, createFeedType, purchaseFeed, useFeed, feedQuantity, parseWaterEntries, saveDailyRecord, recordPopulationEvent, loadPricingForBatch, savePricingSettings, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale, saveHealth, saveDrug, saveVaccination, loadHealthForBatch, markBatchSold, reopenBatch, addHouse } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="reports" tab="farm-report"><section id="reports" className="card full">
          <div className="section-head"><div><p className="eyebrow">REPORTING</p><h2>Farm reports</h2></div>
            <div className="inline-form"><button type="button" onClick={loadReport}>Refresh</button><button type="button" onClick={() => exportCsv(() => reportApi.farmCsv(), "farm-report.csv")}>Export CSV</button><button type="button" onClick={() => exportPdf(() => reportApi.farmPdf(), "farm-report.pdf")}>Export PDF</button></div>
          </div>
          {farmReport ? <>
            <div className="metric-grid">
              {[
                ["Total birds", formatCount(farmReport.dashboard?.totalBirds)],
                ["Mortality", formatCount(farmReport.dashboard?.mortality)],
                ["Feed types", formatCount(farmReport.feed?.length)],
                ["Expenses", formatMoney(farmReport.dashboard?.expensesMinor, farm.currency)],
                ["Revenue", formatMoney(farmReport.dashboard?.revenueMinor, farm.currency)],
                ["Profit", formatMoney(farmReport.dashboard?.profitMinor, farm.currency)],
                ["Egg batches", formatCount(farmReport.eggProduction?.length)],
                ["Broiler batches", formatCount(farmReport.broilerGrowth?.length)],
                ["Sales records", formatCount(farmReport.sales?.length)],
                ["Inventory items", formatCount(farmReport.inventory?.length)]
              ].map(([label,value]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong></div>)}
            </div>
            <p className="muted">As of {farmReport.asOf}. All report metrics are assembled from the same calculation services used by the dashboards.</p>
          </> : <p className="muted">Report is loading…</p>}
        </section></PageSection>

      <PageSection page="reports" tab="batch-report"><section id="batch-reports" className="card full">
          <div className="section-head"><div><p className="eyebrow">REPORTING</p><h2>Batch reports</h2></div>
            <div className="inline-form">
              <select aria-label="Batch report" value={selectedBatchId} onChange={e => { setSelectedBatchId(e.target.value); rememberLastBatchId(e.target.value); }}>
                <option value="">Select a batch</option>{batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code} · {batch.type}</option>)}
              </select>
              <button type="button" onClick={() => loadBatchReport()}>Refresh</button>
              <button type="button" disabled={!selectedBatchId} onClick={() => exportCsv(() => reportApi.batchCsv(selectedBatchId), "batch-report.csv")}>Export CSV</button>
              <button type="button" disabled={!selectedBatchId} onClick={() => exportPdf(() => reportApi.batchPdf(selectedBatchId), "batch-report.pdf")}>Export PDF</button>
            </div>
          </div>
          {batchReport ? <>
            <div className="metric-grid">
              {[
                ["Initial birds", batchReport.population?.initialBirds],
                ["Current birds", batchReport.population?.currentBirds],
                ["Mortality", batchReport.mortality],
                ["Feed", formatQuantity(batchReport.feed?.totalQuantityMilli)],
                ["Costs", formatMoney(batchReport.costs?.expensesMinor, farm.currency)],
                ["Revenue", formatMoney(batchReport.revenueMinor, farm.currency)],
                ["Profit", formatMoney(batchReport.profitMinor, farm.currency)],
                ["Health records", batchReport.health?.healthRecords?.length],
                ["Sales records", batchReport.sales?.length]
              ].map(([label,value]) => <div className="metric-card" key={label}><span>{label}</span><strong>{value ?? "—"}</strong></div>)}
            </div>
            <div className="dashboard-columns">
              <div className="subsection"><h3>Production</h3>
                {batchReport.production?.type === "LAYER" ? <>
                  <div className="dashboard-list">
                    <div><span>Good eggs</span><strong>{batchReport.production.eggInventory?.goodCollected ?? "—"}</strong></div>
                    <div><span>Egg inventory</span><strong>{batchReport.production.eggInventory?.goodRemaining ?? "—"}</strong></div>
                    <div><span>Good egg rate</span><strong>{formatPercent(batchReport.production.eggQuality?.goodRatePercent)}</strong></div>
                  </div>
                </> : <>
                  <div className="dashboard-list">
                    <div><span>Average weight</span><strong>{formatKg(batchReport.production.growth?.currentAverageWeightKg)}</strong></div>
                    <div><span>Growth</span><strong>{formatKg(batchReport.production.growth?.liveWeightGainKg)}</strong></div>
                    <div><span>FCR</span><strong>{formatMetric(batchReport.production.growth?.fcr)}</strong></div>
                  </div>
                </>}
              </div>
              <div className="subsection"><h3>Health</h3>
                <div className="dashboard-list">
                  <div><span>Health records</span><strong>{batchReport.health?.healthRecords?.length ?? 0}</strong></div>
                  <div><span>Drug records</span><strong>{batchReport.health?.drugs?.length ?? 0}</strong></div>
                  <div><span>Vaccinations</span><strong>{batchReport.health?.vaccinations?.length ?? 0}</strong></div>
                </div>
              </div>
            </div>
            <p className="muted">As of {batchReport.asOf}. Population, feed, production, costs, revenue and profit reuse the dashboard calculation services.</p>
          </> : <p className="muted">{selectedBatchId ? "Batch report is loading…" : "Select a batch to inspect its report."}</p>}
        </section></PageSection>
    </div>
  );
}
