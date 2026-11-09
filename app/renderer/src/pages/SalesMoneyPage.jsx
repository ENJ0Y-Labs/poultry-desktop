import { useContext, useState } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import { ModalForm } from "../components/Modal.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
// eslint-disable-next-line no-unused-vars
import EmptyState from "../components/EmptyState.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";

const saleTypeLabel = type => type === "EGG" ? "Eggs" : type === "BROILER" ? "Broiler birds" : type === "TURKEY" ? "Turkey birds" : String(type || "Unknown").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());

export default function SalesMoneyPage() {
  const { error } = useContext(FarmManagerContext);
  const [customerOpenSignal, setCustomerOpenSignal] = useState(0);
  const { formatMoney, farm, batches, expenseForm, setExpenseForm, expenses, expenseBatchFilter, setExpenseBatchFilter, customers, customerForm, setCustomerForm, emptyCustomer, emptyExpense, editingCustomerId, editCustomer, cancelCustomerEdit, sales, saving, expenseCategories, loadExpenses, saveCustomer, saveExpense } = useContext(FarmManagerContext);

  return (
    <PageTemplate className="grid">
      <PageSection page="sales-money" tab="customers"><section id="customers" className="card full">
          <div className="section-head">
            <h2>Customers</h2>
            <p className="muted">Keep reusable customer records for egg and broiler sales. Type-specific sale records also retain the customer name used when the sale was recorded.</p>
          </div>
          <ModalForm title="Customer" description="Add or update a reusable customer record." triggerLabel={editingCustomerId ? "Edit customer" : "Add customer"} openSignal={customerOpenSignal} loading={saving} error={error} onDiscard={() => { setCustomerForm(emptyCustomer); cancelCustomerEdit(); }}>
<form className="inline-form" onSubmit={saveCustomer}>
            <label className="field"><span>Customer name</span><input required value={customerForm.name} onChange={e=>setCustomerForm({...customerForm,name:e.target.value})}/></label>
            <label className="field"><span>Phone</span><input value={customerForm.phone} onChange={e=>setCustomerForm({...customerForm,phone:e.target.value})}/></label>
            <label className="field"><span>Notes</span><input value={customerForm.notes} onChange={e=>setCustomerForm({...customerForm,notes:e.target.value})}/></label>
            <button disabled={saving}>{editingCustomerId ? "Save customer" : "Add customer"}</button>{editingCustomerId && <button type="button" className="secondary-button" onClick={cancelCustomerEdit}>Cancel</button>}
          </form>
</ModalForm>
          <DataTable caption="Customers" columns={[
              { key: "name", label: "Name" }, { key: "phone", label: "Phone", render: row => row.phone || "—" },
              { key: "notes", label: "Notes", render: row => row.notes || "—" },
              { key: "action", label: "Action", render: row => <button type="button" className="secondary-button" onClick={() => { editCustomer(row); setCustomerOpenSignal(value => value + 1); }}>Edit</button> },
            ]} rows={customers} emptyMessage="No customers yet." />
        </section></PageSection>

      <PageSection page="sales-money" tab="sales"><section id="sales" className="card full">
          <div className="section-head">
            <h2>Sales</h2>
            <p className="muted">Common commercial ledger for Layer egg, Broiler bird, and Turkey bird sales. Type-specific rules remain in their production modules.</p>
          </div>
          <div className="subsection">
            <DataTable caption="Sales ledger" columns={[
                { key: "saleDate", label: "Date" }, { key: "saleType", label: "Type", render: row => saleTypeLabel(row.saleType) },
                { key: "customerName", label: "Customer" }, { key: "batchId", label: "Batch", render: row => batches.find(batch => batch.id === row.batchId)?.code || row.batchId },
                { key: "quantity", label: "Quantity", render: row => row.quantity?.toString() }, { key: "unit", label: "Unit" },
                { key: "unitPriceMinor", label: "Unit price", render: row => formatMoney(row.unitPriceMinor, farm.currency) },
                { key: "totalAmountMinor", label: "Total", render: row => formatMoney(row.totalAmountMinor, farm.currency) },
              ]} rows={sales} emptyMessage="No sales recorded yet. Record an egg or meat-bird sale in Production to see it here." />
          </div>
        </section></PageSection>

      <PageSection page="sales-money" tab="expenses"><section id="expenses" className="card full">
          <div className="section-head">
            <h2>Expenses</h2>
            <p className="muted">Record farm-level or batch-associated expenses. Enter amounts in naira (₦); the app stores exact kobo values.</p>
          </div>
          <ModalForm title="Record expense" description="Record a farm-level or batch-associated expense." triggerLabel="Record expense" loading={saving} error={error} onDiscard={() => setExpenseForm(emptyExpense)}>
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
</ModalForm>

          <div className="subsection">
            <div className="section-head">
              <h3>Expense history</h3>
              <label className="field"><span>Filter by batch</span><select value={expenseBatchFilter} onChange={e=>{setExpenseBatchFilter(e.target.value);loadExpenses(e.target.value);}}>
                <option value="">All farm expenses</option>
                {batches.map(b=><option key={b.id} value={b.id}>{b.code}</option>)}
              </select></label>
            </div>
            <DataTable caption="Expenses ledger" columns={[
                { key: "occurredDate", label: "Date" }, { key: "description", label: "Description" },
                { key: "amountMinor", label: "Amount", render: row => formatMoney(row.amountMinor, farm.currency) },
                { key: "category", label: "Category" },
                { key: "batchId", label: "Association", render: row => row.batchId ? (batches.find(batch => batch.id === row.batchId)?.code || row.batchId) : "Farm" },
              ]} rows={expenses} emptyMessage="No expenses recorded." />
          </div>
        </section></PageSection>
    </PageTemplate>
  );
}
