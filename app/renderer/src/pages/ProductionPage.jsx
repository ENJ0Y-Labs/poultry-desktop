import { useContext } from "react";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ValidatedForm from "../components/ValidatedForm.jsx";
import { productionTrendApi } from "../services/api.js";
// eslint-disable-next-line no-unused-vars
import DateRangeFilter from "../components/DateRangeFilter.jsx";
import { filterRowsByDateRange } from "../utils/dateRange.js";
// eslint-disable-next-line no-unused-vars
import ProductionTrendChart from "../components/ProductionTrendChart.jsx";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import { ModalForm } from "../components/Modal.jsx";
import { todayLocalDate } from "../utils/farmUx.js";
// eslint-disable-next-line no-unused-vars
import EmptyState from "../components/EmptyState.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
// eslint-disable-next-line no-unused-vars
import MetricHelp from "../components/MetricHelp.jsx";
// eslint-disable-next-line no-unused-vars
import { ProductionDistributionCharts } from "../components/AnalyticsCharts.jsx";

export default function ProductionPage() {
  const { error } = useContext(FarmManagerContext);
  const { formatMoney, farm, batches, customers, dateRange, setDateRange, applicationSettings, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, broilerWeights, broilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, emptyWeight, emptyBirdSale, pricing, pricingForm, setPricingForm, eggInventory, eggCollections, eggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, crateSize, saving, loadPricingForBatch, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale } = useContext(FarmManagerContext);

  const periodEggCollections = filterRowsByDateRange(eggCollections, dateRange, "recordDate");
  const periodEggSales = filterRowsByDateRange(eggSales, dateRange, "recordDate");
  const periodBroilerWeights = filterRowsByDateRange(broilerWeights, dateRange, "recordDate");
  const periodBroilerSales = filterRowsByDateRange(broilerSales, dateRange, "recordDate");

  return (
    <PageTemplate className="grid pomas-page page-production">
      <PageSection page="production" tab="eggs"><section id="eggs" className="card full">
          <div className="section-head">
            <div>
              <h2>Layer egg production <MetricHelp id="hdp-formula-help" label="HDP formula">Hen-day production (HDP) is calculated for each date: total eggs collected that day (good + cracked) ÷ live layer birds on that date × 100. The numerator is in eggs and the denominator is birds, so the result is a percentage for that day. For example, 850 eggs from 1,000 live hens = 85%. The backend calculates the historical live-bird count; missing egg records are shown as gaps, not zero.</MetricHelp></h2>
              <p className="muted">Only LAYER batches can record eggs. Good and cracked eggs are stored as individual eggs. Cracked eggs remain historical and are never sellable.</p>
            </div>
            <DateRangeFilter value={dateRange} timezone={farm?.timezone} dateFormat={applicationSettings?.dateFormat} onChange={setDateRange} />
          </div>

          <div className="subsection">
            <h3>Egg batch</h3>
            <label className="field"><span>Layer batch</span><select value={eggBatchId} onChange={e => {
              const id = e.target.value;
              setEggBatchId(id);
              setEggCollectionForm(form => ({ ...form, batchId: id }));
              setEggSaleForm(form => ({ ...form, batchId: id }));
              loadEggsForBatch(id);
            }}>
              <option value="">Select a layer batch</option>
              {batches.filter(b => b.type === "LAYER").map(b => <option key={b.id} value={b.id}>{b.code}</option>)}
            </select></label>
          </div>
          {!batches.some(batch => batch.type === "LAYER") && <EmptyState variant="not-applicable" heading="No layer batches available" description="Create a LAYER batch in Flocks before recording egg production." compact />}

          {eggBatchId && (
            <>
              <ProductionTrendChart
                batchId={eggBatchId}
                title="Layer HDP%"
                titleHelp={<MetricHelp id="production-hdp-chart-help" label="HDP chart formula">HDP% for each date = total eggs collected that date (good + cracked, in eggs) ÷ live layer birds on that date × 100. The backend calculates the historical live-bird count. Missing egg records are gaps, not zero.</MetricHelp>}
                description="Daily eggs collected (good + cracked) as a percentage of the historical live-bird population."
                unit="percent"
                loadTrend={productionTrendApi.layerHdp}
                dateRange={dateRange}
              />
              <ProductionDistributionCharts eggCollections={periodEggCollections} batch={batches.find(batch => batch.id === eggBatchId)} dateRange={dateRange} />
              <div className="subsection">
                <h3>Egg inventory</h3>
                <p className="muted">Current egg balances are a live snapshot and are not rewound by the selected activity period.</p>
                <DataTable caption="Egg inventory" columns={[
                    { key: "goodCollected", label: "Good collected" }, { key: "goodSold", label: "Good sold" },
                    { key: "goodRemaining", label: "Good remaining" }, { key: "crackedCollected", label: "Cracked" },
                    { key: "totalCollected", label: "Total collected" },
                  ]} rows={[{ id: "egg-inventory", goodCollected: eggInventory?.goodCollected ?? "—", goodSold: eggInventory?.goodSold ?? "—", goodRemaining: eggInventory?.goodRemaining ?? "—", crackedCollected: eggInventory?.crackedCollected ?? "—", totalCollected: eggInventory?.totalCollected ?? "—" }]} />
              </div>

              <div className="subsection">
                <h3>Record egg production</h3>
                <ModalForm title="Egg collection" description="Record good and cracked eggs collected from this layer batch." triggerLabel="Record collection" loading={saving} error={error} onDiscard={() => setEggCollectionForm(form => ({ batchId: form.batchId, date: todayLocalDate(), good: "", cracked: "", notes: "" }))}>
<ValidatedForm className="inline-form" onSubmit={saveEggCollection}>
                  <label className="field"><span>Collection date</span><input required type="date" value={eggCollectionForm.date} onChange={e=>setEggCollectionForm({...eggCollectionForm,date:e.target.value})}/></label>
                  <label className="field"><span>Good eggs</span><input required min="0" step="1" type="number" value={eggCollectionForm.good} onChange={e=>setEggCollectionForm({...eggCollectionForm,good:e.target.value})}/></label>
                  <label className="field"><span>Cracked eggs</span><input required min="0" step="1" type="number" value={eggCollectionForm.cracked} onChange={e=>setEggCollectionForm({...eggCollectionForm,cracked:e.target.value})}/></label>
                  <label className="field"><span>Notes</span><input value={eggCollectionForm.notes} onChange={e=>setEggCollectionForm({...eggCollectionForm,notes:e.target.value})}/></label>
                  <button disabled={saving}>Record collection</button>
                </ValidatedForm>
</ModalForm>
                <DataTable caption="Egg collections" columns={[
                    { key: "recordDate", label: "Date" }, { key: "good", label: "Good" }, { key: "cracked", label: "Cracked" },
                    { key: "total", label: "Total" }, { key: "notes", label: "Notes", render: row => row.notes || "—" },
                  ]} rows={periodEggCollections} emptyMessage="No egg collections recorded for this layer batch. Record the first collection above." />
              </div>

              <div className="subsection">
                <h3>Egg sales</h3>
                <p className="muted">Current crate size: {crateSize} eggs. The sale stores the crate size used at the time, so changing settings does not rewrite history.</p>
                <ModalForm title="Egg sale" description="Record a sale from the available good-egg inventory." triggerLabel="Record egg sale" loading={saving} error={error} onDiscard={() => setEggSaleForm(form => ({ batchId: form.batchId, date: todayLocalDate(), customer: "", customerId: "", crates: "", pricePerCrateMinor: "" }))}>
<ValidatedForm className="inline-form" onSubmit={saveEggSale}>
                  <label className="field"><span>Sale date</span><input required type="date" value={eggSaleForm.date} onChange={e=>setEggSaleForm({...eggSaleForm,date:e.target.value})}/></label>
                  <label className="field"><span>Saved customer</span><select value={eggSaleForm.customerId} onChange={e=>{const id=e.target.value;const customer=customers.find(c=>c.id===id);setEggSaleForm({...eggSaleForm,customerId:id,customer:customer?.name||""});}}>
                    <option value="">Walk-in sale / no saved customer</option>
                    {customers.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}
                  </select></label>
                  {!eggSaleForm.customerId && <label className="field"><span>Walk-in customer name</span><input required maxLength="120" value={eggSaleForm.customer} onChange={e=>setEggSaleForm({...eggSaleForm,customer:e.target.value})} placeholder="Enter buyer name"/></label>}
                  <label className="field"><span>Crates e.g. 0.5 or 2.5</span><input required min="0.001" step="0.001" type="number" value={eggSaleForm.crates} onChange={e=>setEggSaleForm({...eggSaleForm,crates:e.target.value})}/></label>
                  <label className="field"><span>Price per crate (₦)</span><input required type="text" inputMode="decimal" value={eggSaleForm.pricePerCrateMinor} onChange={e=>setEggSaleForm({...eggSaleForm,pricePerCrateMinor:e.target.value})}/></label>
                  <button disabled={saving}>Record sale</button>
                </ValidatedForm>
</ModalForm>
                <DataTable caption="Egg sales" columns={[
                    { key: "recordDate", label: "Date" }, { key: "customer", label: "Customer" },
                    { key: "crates", label: "Crates", render: row => row.crates.toString() }, { key: "soldEggs", label: "Eggs" },
                    { key: "pricePerCrateMinor", label: "Price / crate", render: row => formatMoney(row.pricePerCrateMinor, farm.currency) },
                    { key: "totalAmountMinor", label: "Total", render: row => formatMoney(row.totalAmountMinor, farm.currency) },
                  ]} rows={periodEggSales} emptyMessage="No egg sales recorded for this layer batch yet." />
              </div>
            </>
          )}
        </section></PageSection>

      <PageSection page="production" tab="broiler"><section className="card full">
          <div className="section-head">
            <div>
              <h2>Meat-bird production</h2>
              <p className="muted">BROILER and TURKEY batches expose weight, growth, FCR, and bird sales. FCR is a batch-to-date snapshot; the weight trend follows the selected reporting period.</p>
            </div>
            <DateRangeFilter value={dateRange} timezone={farm?.timezone} dateFormat={applicationSettings?.dateFormat} onChange={setDateRange} />
          </div>

          <div className="subsection">
            <label className="field"><span>Meat-production batch</span><select value={broilerBatchId} onChange={e => {
              const id = e.target.value;
              setBroilerBatchId(id);
              setWeightForm(form => ({ ...form, batchId: id }));
              setBirdSaleForm(form => ({ ...form, batchId: id }));
              setPricingForm(form => ({ ...form, quantity: "" }));
              loadBroilerForBatch(id);
            }}>
              <option value="">Select a batch</option>
              {batches.filter(b => b.type === "BROILER" || b.type === "TURKEY").map(b =>
                <option key={b.id} value={b.id}>{b.code} · {b.status}</option>
              )}
            </select></label>
          </div>
          {!batches.some(batch => batch.type === "BROILER" || batch.type === "TURKEY") && <EmptyState variant="not-applicable" heading="No meat-bird batches available" description="Create a BROILER or TURKEY batch in Flocks before recording weights and bird sales." compact />}

          {broilerBatchId && (
            <>
              <ProductionTrendChart
                batchId={broilerBatchId}
                title="Meat-bird average weight"
                description="Average sampled bird weight by record date, in kilograms. Days without weight records appear as gaps."
                unit="kg"
                loadTrend={productionTrendApi.broilerAverageWeight}
                dateRange={dateRange}
              />
              <ProductionDistributionCharts weights={periodBroilerWeights} batch={batches.find(batch => batch.id === broilerBatchId)} dateRange={dateRange} />
              <div className="subsection">
                <h3>Growth summary</h3>
                <DataTable caption="Meat-bird growth summary" columns={[
                    { key: "averageWeightKg", label: "Average weight", render: row => row.averageWeightKg == null ? "—" : `${row.averageWeightKg} kg` },
                    { key: "weightGainKg", label: "Weight gain", render: row => row.weightGainKg == null ? "—" : `${row.weightGainKg} kg` },
                    { key: "feedConsumedKg", label: "Feed consumed (converted)", render: row => row.feedConsumedKg == null ? "—" : `${row.feedConsumedKg} kg` },
                    { key: "fcr", label: "FCR", render: row => row.fcr ?? "—" }, { key: "trend", label: "Trend records", render: row => row.trend?.length ?? 0 },
                  ]} rows={broilerGrowth ? [{ id: "growth-summary", ...broilerGrowth }] : []} emptyMessage="No growth summary available until weight records have been entered." notApplicable={!broilerBatchId} />
                <p className="muted">FCR uses converted kilograms of all recorded feed divided by live-weight gain. Configure each non-kg feed type’s kg-per-unit factor in Feed management. If any consumed feed type has an unknown conversion, feed consumed and FCR display — until the factor is configured.</p>
              </div>

              <div className="subsection">
                <h3>Record weight</h3>
                <ModalForm title="Weight record" description="Record the sampled bird count and total sample weight." triggerLabel="Record weight" loading={saving} error={error} onDiscard={() => setWeightForm(form => ({ ...emptyWeight, batchId: form.batchId }))}>
<ValidatedForm className="inline-form" onSubmit={saveWeight}>
                  <label className="field"><span>Record date</span><input required type="date" value={weightForm.date} onChange={e=>setWeightForm({...weightForm,date:e.target.value})}/></label>
                  <label className="field"><span>Sampled birds</span><input required min="1" type="number" value={weightForm.sampleQuantity} onChange={e=>setWeightForm({...weightForm,sampleQuantity:e.target.value})}/></label>
                  <label className="field"><span>Total sample weight (kg)</span><input required min="0.001" step="0.001" type="number" value={weightForm.totalWeightKg} onChange={e=>setWeightForm({...weightForm,totalWeightKg:e.target.value})}/></label>
                  <label className="field"><span>Notes</span><input value={weightForm.notes} onChange={e=>setWeightForm({...weightForm,notes:e.target.value})}/></label>
                  <button disabled={saving}>Record weight</button>
                </ValidatedForm>
</ModalForm>
                <DataTable caption="Meat-bird weight records" columns={[
                    { key: "recordDate", label: "Date" }, { key: "sampleQuantity", label: "Sample" }, { key: "totalWeightKg", label: "Total kg" },
                    { key: "averageWeightKg", label: "Average kg" }, { key: "weightGainKg", label: "Gain kg/bird" },
                  ]} rows={periodBroilerWeights} emptyMessage="No weight records yet. Record a sample above to start the growth trend." />
              </div>

              <div className="subsection">
                <h3>Cost-based pricing</h3>
                <p className="muted">Actual cost comes from the backend bird-cost ledger. Prices are calculated by Spring Boot.</p>
                <ValidatedForm className="inline-form" onSubmit={async e=>{e.preventDefault(); await loadPricingForBatch(broilerBatchId, pricingForm.quantity, pricingForm.date);}}>
                  <label className="field"><span>Bird quantity</span><input required min="1" type="number" value={pricingForm.quantity} onChange={e=>setPricingForm({...pricingForm,quantity:e.target.value})}/></label>
                  <label className="field"><span>Pricing date</span><input required type="date" value={pricingForm.date} onChange={e=>setPricingForm({...pricingForm,date:e.target.value})}/></label>
                  <button disabled={saving}>Calculate price</button>
                </ValidatedForm>
                {pricing && <DataTable caption="Cost-based pricing calculation" columns={[
                    { key: "actualCostMinor", label: "Actual cost", render: row => formatMoney(row.actualCostMinor, farm.currency) },
                    { key: "targetMarginPercent", label: "Target margin", render: row => `${row.targetMarginPercent ?? "—"}%` },
                    { key: "targetPricePerBirdMinor", label: "Target price / bird", render: row => formatMoney(row.targetPricePerBirdMinor, farm.currency) },
                    { key: "workingMarginPercent", label: "Working margin", render: row => `${row.workingMarginPercent ?? "—"}%` },
                    { key: "workingPricePerBirdMinor", label: "Working price / bird", render: row => formatMoney(row.workingPricePerBirdMinor, farm.currency) },
                  ]} rows={[{ id: "pricing", ...pricing }]} />}
              </div>

              <div className="subsection">
                <h3>Bird sales</h3>
                <ModalForm title="Bird sale" description="Record a partial or full sale of birds from this batch." triggerLabel="Record bird sale" loading={saving} error={error} onDiscard={() => setBirdSaleForm(form => ({ ...emptyBirdSale, batchId: form.batchId }))}>
<ValidatedForm className="inline-form" onSubmit={saveBirdSale}>
                  <label className="field"><span>Sale date</span><input required type="date" value={birdSaleForm.date} onChange={e=>setBirdSaleForm({...birdSaleForm,date:e.target.value})}/></label>
                  <label className="field"><span>Quantity</span><input required min="1" type="number" value={birdSaleForm.quantity} onChange={e=>setBirdSaleForm({...birdSaleForm,quantity:e.target.value})}/></label>
                  <label className="field"><span>Price per bird (₦)</span><input required type="text" inputMode="decimal" value={birdSaleForm.pricePerBirdMinor} onChange={e=>setBirdSaleForm({...birdSaleForm,pricePerBirdMinor:e.target.value})}/></label>
                  <label className="field"><span>Saved customer</span><select value={birdSaleForm.customerId} onChange={e=>{const id=e.target.value;const customer=customers.find(c=>c.id===id);setBirdSaleForm({...birdSaleForm,customerId:id,customer:customer?.name||""});}}>
                    <option value="">Walk-in sale / no saved customer</option>
                    {customers.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}
                  </select></label>
                  {!birdSaleForm.customerId && <label className="field"><span>Walk-in customer name</span><input required maxLength="120" value={birdSaleForm.customer} onChange={e=>setBirdSaleForm({...birdSaleForm,customer:e.target.value})} placeholder="Enter buyer name"/></label>}
                  <button disabled={saving}>Record bird sale</button>
                </ValidatedForm>
</ModalForm>
                <DataTable caption="Meat-bird sales" columns={[
                    { key: "recordDate", label: "Date" }, { key: "quantity", label: "Quantity" },
                    { key: "pricePerBirdMinor", label: "Price / bird", render: row => formatMoney(row.pricePerBirdMinor, farm.currency) },
                    { key: "totalAmountMinor", label: "Total", render: row => formatMoney(row.totalAmountMinor, farm.currency) },
                    { key: "customer", label: "Customer" },
                  ]} rows={periodBroilerSales} emptyMessage="No bird sales recorded for this meat-bird batch yet." />
              </div>
            </>
          )}
        </section></PageSection>
    </PageTemplate>
  );
}
