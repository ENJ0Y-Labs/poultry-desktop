import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import PageSection from "../components/PageSection.jsx";

export default function HealthPage() {
  const { formatMoney, formatQuantity, formatPercent, formatKg, formatMetric, formatCount, displayApiError, emptyWeight, emptyBirdSale, emptyPricing, emptyFarm, emptyHouse, emptyBatch, emptyCost, emptyFeedType, emptyFeedPurchase, emptyFeedUsage, emptyDaily, emptyPopulationEvent, emptyHealth, emptyDrug, emptyVaccination, emptyExpense, emptyCustomer, emptyInventoryItem, emptyInventoryMovement, farm, setFarm, farmForm, setFarmForm, houses, setHouses, batches, setBatches, batchForm, setBatchForm, costForm, setCostForm, feedTypes, setFeedTypes, feedInventory, setFeedInventory, feedTypeForm, setFeedTypeForm, feedPurchaseForm, setFeedPurchaseForm, feedUsageForm, setFeedUsageForm, dailyForm, setDailyForm, dailyRecord, setDailyRecord, mortalityForm, setMortalityForm, cullingForm, setCullingForm, healthForm, setHealthForm, drugForm, setDrugForm, vaccinationForm, setVaccinationForm, healthRecords, setHealthRecords, drugRecords, setDrugRecords, vaccinationRecords, setVaccinationRecords, expenseForm, setExpenseForm, expenses, setExpenses, expenseBatchFilter, setExpenseBatchFilter, inventoryItems, setInventoryItems, inventoryItemForm, setInventoryItemForm, inventoryMovementForm, setInventoryMovementForm, inventoryMovements, setInventoryMovements, customers, setCustomers, customerForm, setCustomerForm, sales, setSales, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, setBroilerGrowth, broilerWeights, setBroilerWeights, broilerSales, setBroilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, pricingSettings, setPricingSettings, pricing, setPricing, pricingForm, setPricingForm, eggInventory, setEggInventory, eggCollections, setEggCollections, eggSales, setEggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, houseForm, setHouseForm, crateSize, setCrateSize, defaultWater, setDefaultWater, waterSizes, setWaterSizes, loading, setLoading, saving, setSaving, error, setError, dashboard, setDashboard, attention, setAttention, auditRows, setAuditRows, expenseCategories, setExpenseCategories, expenseCategoryForm, setExpenseCategoryForm, account, setAccount, accountForm, setAccountForm, applicationSettings, setApplicationSettings, backupSettings, setBackupSettings, selectedBatchId, setSelectedBatchId, batchDashboard, setBatchDashboard, farmReport, setFarmReport, batchReport, setBatchReport, load, loadReport, loadBatchReport, exportCsv, exportPdf, loadBatchDashboard, loadDashboard, submitFarm, saveFarm, createExpenseCategory, archiveExpenseCategory, saveAccount, saveApplicationSettings, chooseBackupSettingsDirectory, saveBackupSettings, createBackup, restoreBackup, saveSettings, loadExpenses, loadSales, saveCustomer, saveInventoryItem, loadInventoryMovements, saveInventoryMovement, saveExpense, createBatch, addBirdCost, createFeedType, purchaseFeed, useFeed, feedQuantity, parseWaterEntries, saveDailyRecord, recordPopulationEvent, loadPricingForBatch, savePricingSettings, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale, saveHealth, saveDrug, saveVaccination, loadHealthForBatch, markBatchSold, reopenBatch, addHouse } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="health" tab="health"><section id="health" className="card full">
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
              {drugRecords.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.drug}</span><span>{row.quantity}</span><span>{formatMoney(row.costMinor, farm.currency)}</span><span>{row.reason}</span></div>)}
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
        </section></PageSection>
    </div>
  );
}
