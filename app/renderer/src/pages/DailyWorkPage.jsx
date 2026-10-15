import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import PageSection from "../components/PageSection.jsx";

export default function DailyWorkPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, displayApiError, emptyWeight, emptyBirdSale, emptyPricing, emptyFarm, emptyHouse, emptyBatch, emptyCost, emptyFeedType, emptyFeedPurchase, emptyFeedUsage, emptyDaily, emptyPopulationEvent, emptyHealth, emptyDrug, emptyVaccination, emptyExpense, emptyCustomer, emptyInventoryItem, emptyInventoryMovement, farm, setFarm, farmForm, setFarmForm, houses, setHouses, batches, setBatches, batchForm, setBatchForm, costForm, setCostForm, feedTypes, setFeedTypes, feedInventory, setFeedInventory, feedTypeForm, setFeedTypeForm, feedPurchaseForm, setFeedPurchaseForm, feedUsageForm, setFeedUsageForm, dailyForm, setDailyForm, dailyRecord, setDailyRecord, mortalityForm, setMortalityForm, cullingForm, setCullingForm, healthForm, setHealthForm, drugForm, setDrugForm, vaccinationForm, setVaccinationForm, healthRecords, setHealthRecords, drugRecords, setDrugRecords, vaccinationRecords, setVaccinationRecords, expenseForm, setExpenseForm, expenses, setExpenses, expenseBatchFilter, setExpenseBatchFilter, inventoryItems, setInventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, setInventoryMovements, customers, setCustomers, customerForm, setCustomerForm, sales, setSales, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, setBroilerGrowth, broilerWeights, setBroilerWeights, broilerSales, setBroilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, pricingSettings, setPricingSettings, pricing, setPricing, pricingForm, setPricingForm, eggInventory, setEggInventory, eggCollections, setEggCollections, eggSales, setEggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, houseForm, setHouseForm, crateSize, setCrateSize, defaultWater, setDefaultWater, waterSizes, setWaterSizes, loading, setLoading, saving, setSaving, error, setError, dashboard, setDashboard, attention, setAttention, auditRows, setAuditRows, expenseCategories, setExpenseCategories, expenseCategoryForm, setExpenseCategoryForm, account, setAccount, accountForm, setAccountForm, applicationSettings, setApplicationSettings, backupSettings, setBackupSettings, selectedBatchId, setSelectedBatchId, batchDashboard, setBatchDashboard, farmReport, setFarmReport, batchReport, setBatchReport, load, loadReport, loadBatchReport, exportCsv, exportPdf, loadBatchDashboard, loadDashboard, submitFarm, saveFarm, createExpenseCategory, archiveExpenseCategory, saveAccount, saveApplicationSettings, chooseBackupSettingsDirectory, saveBackupSettings, createBackup, restoreBackup, saveSettings, loadExpenses, loadSales, saveCustomer, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, saveExpense, createBatch, addBirdCost, createFeedType, purchaseFeed, useFeed, feedQuantity, parseWaterEntries, saveDailyRecord, recordPopulationEvent, loadPricingForBatch, savePricingSettings, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale, saveHealth, saveDrug, saveVaccination, loadHealthForBatch, markBatchSold, reopenBatch, addHouse } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="daily-work" tab="daily-work"><section id="operations" className="card full">
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
        </section></PageSection>
    </div>
  );
}
