import { useContext, useState } from "react";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import { ModalForm } from "../components/Modal.jsx";
// eslint-disable-next-line no-unused-vars -- JSX component usage is not detected by the core rule
import ContextDrawer, { DrawerDetails } from "../components/ContextDrawer.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "../components/PageSection.jsx";
// eslint-disable-next-line no-unused-vars
import PageTemplate from "../components/PageTemplate.jsx";
// eslint-disable-next-line no-unused-vars
import DataTable from "../components/DataTable.jsx";

const saleTypeLabel = type => type === "EGG" ? "Eggs" : type === "BROILER" ? "Broiler birds" : type === "TURKEY" ? "Turkey birds" : String(type || "Unknown").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
const customerKey = value => String(value || "").trim().toLocaleLowerCase();

export default function SalesMoneyPage() {
  const { error } = useContext(FarmManagerContext);
  const [customerOpenSignal, setCustomerOpenSignal] = useState(0);
  const [drawerRecord, setDrawerRecord] = useState(null);
  const { formatMoney, farm, batches, expenseForm, setExpenseForm, expenses, expenseBatchFilter, setExpenseBatchFilter, customers, customerForm, setCustomerForm, emptyCustomer, emptyExpense, editingCustomerId, editCustomer, cancelCustomerEdit, sales, saving, expenseCategories, loadExpenses, saveCustomer, saveExpense } = useContext(FarmManagerContext);

  function salesForCustomer(customer) {
    return sales
      .filter(sale => sale.customerId && customer.id
        ? sale.customerId === customer.id
        : customerKey(sale.customerName) === customerKey(customer.name))
      .sort((a, b) => String(b.saleDate || "").localeCompare(String(a.saleDate || "")));
  }

  function openCustomer(customer) {
    const history = salesForCustomer(customer);
    const totalSpentMinor = history.reduce((total, sale) => {
      const amount = Number(sale.totalAmountMinor);
      return total + (Number.isSafeInteger(amount) ? amount : 0);
    }, 0);
    const latestSale = history[0];
    const fields = [
      ["Phone", customer.phone],
      ["Email", customer.email],
      ["Address", customer.address],
      ["Status", customer.status],
      ["Total sales", history.length],
      ["Total amount spent", formatMoney(totalSpentMinor, farm.currency)],
      ["Most recent purchase", latestSale?.saleDate],
      ["Notes", customer.notes],
    ].filter(([, value]) => value !== null && value !== undefined && value !== "");
    setDrawerRecord({ kind: "customer", title: customer.name, description: "Customer profile and sales activity recorded under this customer name.", fields, customer, history });
  }

  function openSale(sale) {
    const batch = batches.find(item => item.id === sale.batchId);
    setDrawerRecord({
      title: `Sale · ${sale.saleDate || "Undated"}`,
      description: "Posted sale details. Posted transactions are corrected through the audited correction workflow.",
      fields: [
        ["Sale type", saleTypeLabel(sale.saleType)],
        ["Customer", sale.customerName],
        ["Batch", batch?.code || sale.batchId],
        ["Quantity", sale.quantity == null ? null : `${sale.quantity} ${sale.unit || ""}`.trim()],
        ["Unit price", formatMoney(sale.unitPriceMinor, farm.currency)],
        ["Total", formatMoney(sale.totalAmountMinor, farm.currency)],
      ].filter(([, value]) => value !== null && value !== undefined && value !== ""),
    });
  }

  function openExpense(expense) {
    const batch = batches.find(item => item.id === expense.batchId);
    setDrawerRecord({
      title: expense.description || "Expense",
      description: "Posted expense context. Posted transactions are not edited or deleted.",
      fields: [
        ["Date", expense.occurredDate],
        ["Category", expense.category],
        ["Amount", formatMoney(expense.amountMinor, farm.currency)],
        ["Association", batch?.code || (expense.batchId ? expense.batchId : "Farm-level")],
      ],
    });
  }

  return (
    <PageTemplate className="grid">
      <PageSection page="sales-money" tab="customers"><section id="customers" className="card full">
        <div className="section-head">
          <h2>Customers</h2>
          <p className="muted">Reusable customer records with purchase summaries calculated from the common sales ledger.</p>
        </div>
        <ModalForm title="Customer" description="Add or update a reusable customer record." triggerLabel={editingCustomerId ? "Edit customer" : "Add customer"} openSignal={customerOpenSignal} loading={saving} error={error} onDiscard={() => { setCustomerForm(emptyCustomer); cancelCustomerEdit(); }}>
          <form className="inline-form" onSubmit={saveCustomer}>
            <label className="field"><span>Customer name</span><input required value={customerForm.name} onChange={e => setCustomerForm({ ...customerForm, name: e.target.value })}/></label>
            <label className="field"><span>Phone</span><input value={customerForm.phone} onChange={e => setCustomerForm({ ...customerForm, phone: e.target.value })}/></label>
            <label className="field"><span>Notes</span><input value={customerForm.notes} onChange={e => setCustomerForm({ ...customerForm, notes: e.target.value })}/></label>
            <button disabled={saving}>{editingCustomerId ? "Save customer" : "Add customer"}</button>
            {editingCustomerId && <button type="button" className="secondary-button" onClick={cancelCustomerEdit}>Cancel</button>}
          </form>
        </ModalForm>
        <DataTable caption="Customers" columns={[
          { key: "name", label: "Name" },
          { key: "phone", label: "Phone", render: row => row.phone || "—" },
          { key: "purchases", label: "Purchases", render: row => salesForCustomer(row).length },
          { key: "spent", label: "Total spent", render: row => formatMoney(salesForCustomer(row).reduce((total, sale) => {
            const amount = Number(sale.totalAmountMinor);
            return total + (Number.isSafeInteger(amount) ? amount : 0);
          }, 0), farm.currency) },
          { key: "lastPurchase", label: "Last purchase", render: row => salesForCustomer(row)[0]?.saleDate || "No sales" },
          ...(customers.some(row => row.status != null && row.status !== "") ? [{ key: "status", label: "Status", render: row => row.status || "—" }] : []),
          { key: "details", label: "Details", render: row => <button type="button" className="secondary-button" onClick={() => openCustomer(row)}>View</button> },
        ]} rows={customers} emptyMessage="No customers yet." />
      </section></PageSection>

      <PageSection page="sales-money" tab="sales"><section id="sales" className="card full">
        <div className="section-head">
          <h2>Sales</h2>
          <p className="muted">Common commercial ledger for Layer egg, Broiler bird, and Turkey bird sales. Type-specific rules remain in their production modules.</p>
        </div>
        <div className="subsection">
          <DataTable caption="Sales ledger" columns={[
            { key: "saleDate", label: "Date" },
            { key: "customerName", label: "Customer", render: row => row.customerName || "—" },
            { key: "saleType", label: "Type", render: row => saleTypeLabel(row.saleType) },
            { key: "totalAmountMinor", label: "Total", render: row => formatMoney(row.totalAmountMinor, farm.currency) },
            { key: "details", label: "Details", render: row => <button type="button" className="secondary-button" onClick={() => openSale(row)}>View</button> },
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
            <label className="field"><span>Expense date</span><input required type="date" value={expenseForm.occurredDate} onChange={e => setExpenseForm({ ...expenseForm, occurredDate: e.target.value })}/></label>
            <label className="field"><span>Description</span><input required value={expenseForm.description} onChange={e => setExpenseForm({ ...expenseForm, description: e.target.value })}/></label>
            <label className="field"><span>Amount (₦)</span><input required type="text" inputMode="decimal" value={expenseForm.amountMinor} onChange={e => setExpenseForm({ ...expenseForm, amountMinor: e.target.value })}/></label>
            <label className="field"><span>Category</span><select required value={expenseForm.category} onChange={e => setExpenseForm({ ...expenseForm, category: e.target.value })}>
              {expenseCategories.map(category => <option key={category.id} value={category.name}>{category.name}</option>)}
            </select></label>
            <label className="field"><span>Batch association</span><select value={expenseForm.batchId} onChange={e => setExpenseForm({ ...expenseForm, batchId: e.target.value })}>
              <option value="">Farm-level</option>
              {batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}
            </select></label>
            <button disabled={saving}>Record expense</button>
          </form>
        </ModalForm>
        <div className="subsection">
          <div className="section-head">
            <h3>Expense history</h3>
            <label className="field"><span>Filter by batch</span><select value={expenseBatchFilter} onChange={e => { setExpenseBatchFilter(e.target.value); loadExpenses(e.target.value); }}>
              <option value="">All farm expenses</option>
              {batches.map(batch => <option key={batch.id} value={batch.id}>{batch.code}</option>)}
            </select></label>
          </div>
          <DataTable caption="Expenses ledger" columns={[
            { key: "occurredDate", label: "Date" },
            { key: "description", label: "Description" },
            { key: "amountMinor", label: "Amount", render: row => formatMoney(row.amountMinor, farm.currency) },
            { key: "details", label: "Details", render: row => <button type="button" className="secondary-button" onClick={() => openExpense(row)}>View</button> },
          ]} rows={expenses} emptyMessage="No expenses recorded." />
        </div>
      </section></PageSection>

      <ContextDrawer
        open={Boolean(drawerRecord)}
        onClose={() => setDrawerRecord(null)}
        title={drawerRecord?.title || "Record details"}
        description={drawerRecord?.description}
        size={drawerRecord?.kind === "customer" ? "large" : "medium"}
        footer={drawerRecord?.kind === "customer" ? <button type="button" onClick={() => {
          editCustomer(drawerRecord.customer);
          setDrawerRecord(null);
          setCustomerOpenSignal(value => value + 1);
        }}>Edit customer</button> : null}
      >
        {drawerRecord && <DrawerDetails fields={drawerRecord.fields} />}
        {drawerRecord?.kind === "customer" && (
          <section className="subsection" aria-labelledby="customer-sales-history-title">
            <h3 id="customer-sales-history-title">Sales history</h3>
            {drawerRecord.history.length === 0
              ? <p className="muted">No sales are linked to this customer name yet.</p>
              : <ol className="pomas-drawer-record-list">
                {drawerRecord.history.map((sale, index) => (
                  <li key={sale.id || `${sale.saleDate}-${sale.batchId}-${index}`}>
                    <div><strong>{sale.saleDate || "Undated"} · {saleTypeLabel(sale.saleType)}</strong><span>{sale.quantity ?? "—"} {sale.unit || ""} · {sale.batchId ? (batches.find(batch => batch.id === sale.batchId)?.code || sale.batchId) : "Batch not recorded"}</span></div>
                    <strong>{formatMoney(sale.totalAmountMinor, farm.currency)}</strong>
                  </li>
                ))}
              </ol>}
          </section>
        )}
      </ContextDrawer>
    </PageTemplate>
  );
}
