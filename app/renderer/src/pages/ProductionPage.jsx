import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import PageSection from "../components/PageSection.jsx";

export default function ProductionPage() {
  const { formatMoney, farm, batches, customers, eggBatchId, setEggBatchId, broilerBatchId, setBroilerBatchId, broilerGrowth, broilerWeights, broilerSales, weightForm, setWeightForm, birdSaleForm, setBirdSaleForm, pricing, pricingForm, setPricingForm, eggInventory, eggCollections, eggSales, eggCollectionForm, setEggCollectionForm, eggSaleForm, setEggSaleForm, crateSize, saving, loadPricingForBatch, loadBroilerForBatch, saveWeight, saveBirdSale, loadEggsForBatch, saveEggCollection, saveEggSale } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="production" tab="eggs"><section id="eggs" className="card full">
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
                  {eggSales.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.customer}</span><span>{row.crates.toString()}</span><span>{row.soldEggs}</span><span>{formatMoney(row.pricePerCrateMinor, farm.currency)}</span><span>{formatMoney(row.totalAmountMinor, farm.currency)}</span></div>)}
                  {eggSales.length===0 && <p className="muted empty">No egg sales recorded.</p>}
                </div>
              </div>
            </>
          )}
        </section></PageSection>

      <PageSection page="production" tab="broiler"><section className="card full">
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
                  <div className="row"><span>{formatMoney(pricing.actualCostMinor, farm.currency)}</span><span>{pricing.targetMarginPercent ?? "—"}%</span><span>{formatMoney(pricing.targetPricePerBirdMinor, farm.currency)}</span><span>{pricing.workingMarginPercent ?? "—"}%</span><span>{formatMoney(pricing.workingPricePerBirdMinor, farm.currency)}</span></div>
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
                  {broilerSales.map(row=><div className="row" key={row.id}><span>{row.recordDate}</span><span>{row.quantity}</span><span>{formatMoney(row.pricePerBirdMinor, farm.currency)}</span><span>{formatMoney(row.totalAmountMinor, farm.currency)}</span><span>{row.customer}</span></div>)}
                  {broilerSales.length===0 && <p className="muted empty">No bird sales recorded.</p>}
                </div>
              </div>
            </>
          )}
        </section></PageSection>
    </div>
  );
}
