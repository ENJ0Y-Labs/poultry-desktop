import { useContext } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";

export default function SalesMoneyPage() {
  const { formatMoney, farm, batches, expenseForm, setExpenseForm, expenses, expenseBatchFilter, setExpenseBatchFilter, customers, customerForm, setCustomerForm, sales, saving, expenseCategories, loadExpenses, saveCustomer, saveExpense } = useContext(FarmManagerContext);

  return (
    <div className="grid">
      <PageSection page="sales-money" tab="customers"><section id="customers" className="card full">
          <div className="section-head">
            <h2>Customers</h2>
            <p className="muted">Keep reusable customer records for egg and broiler sales. Type-specific sale records also retain the customer name used when the sale was recorded.</p>
          </div>
          <form className="inline-form" onSubmit={saveCustomer}>
            <label className="field"><span>Customer name</span><input required value={customerForm.name} onChange={e=>setCustomerForm({...customerForm,name:e.target.value})}/></label>
            <label className="field"><span>Phone</span><input value={customerForm.phone} onChange={e=>setCustomerForm({...customerForm,phone:e.target.value})}/></label>
            <label className="field"><span>Notes</span><input value={customerForm.notes} onChange={e=>setCustomerForm({...customerForm,notes:e.target.value})}/></label>
            <button disabled={saving}>Add customer</button>
          </form>
          <div className="table">
            <div className="row header"><span>Name</span><span>Phone</span><span>Notes</span></div>
            {customers.map(row=><div className="row" key={row.id}><span>{row.name}</span><span>{row.phone || "—"}</span><span>{row.notes || "—"}</span></div>)}
            {customers.length===0 && <p className="muted empty">No customers yet.</p>}
          </div>
        </section></PageSection>

      <PageSection page="sales-money" tab="sales"><section id="sales" className="card full">
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
                <span>{formatMoney(row.unitPriceMinor, farm.currency)}</span>
                <span>{formatMoney(row.totalAmountMinor, farm.currency)}</span>
              </div>)}
              {sales.length===0 && <p className="muted empty">No sales recorded.</p>}
            </div>
          </div>
        </section></PageSection>

      <PageSection page="sales-money" tab="expenses"><section id="expenses" className="card full">
          <div className="section-head">
            <h2>Expenses</h2>
            <p className="muted">Record farm-level or batch-associated expenses. Amounts use integer minor currency units.</p>
          </div>
          <form className="inline-form" onSubmit={saveExpense}>
            <label className="field"><span>Expense date</span><input required type="date" value={expenseForm.occurredDate} onChange={e=>setExpenseForm({...expenseForm,occurredDate:e.target.value})}/></label>
            <label className="field"><span>Description</span><input required value={expenseForm.description} onChange={e=>setExpenseForm({...expenseForm,description:e.target.value})}/></label>
            <label className="field"><span>Amount (₦)</span><input required type="text" inputMode="decimal" value={expenseForm.amountMinor} onChange={e=>setExpenseForm({...expenseForm,amountMinor:e.target.value})}/></label>
            <label className="field"><span>Category</span><select required value={expenseForm.category} onChange={e=>setExpenseForm({...expenseForm,category:e.target.value})}>
              {expenseCategories.map(category => <option key={category.id} value={category.name}>{category.name}</option>)}
            </select></label>
            <label className="field"><span>Batch association</span><select value={expenseForm.batchId} onChange={e=>setExpenseForm({...expenseForm,batchId:e.target.value})}>
              <option value="">Farm-level</option>
              {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
            </select></label>
            <button disabled={saving}>Record expense</button>
          </form>

          <div className="subsection">
            <div className="section-head">
              <h3>Expense history</h3>
              <label className="field"><span>Filter by batch</span><select value={expenseBatchFilter} onChange={e=>{setExpenseBatchFilter(e.target.value);loadExpenses(e.target.value);}}>
                <option value="">All farm expenses</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select></label>
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
        </section></PageSection>
    </div>
  );
}
