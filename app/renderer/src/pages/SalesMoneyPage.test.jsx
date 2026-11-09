import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SalesMoneyPage from "./SalesMoneyPage.jsx";
import { FarmManagerContext } from "../components/FarmManagerContext.jsx";
import { NavigationContext } from "../components/NavigationContext.jsx";

const customer = {
  id: "customer-1",
  name: "Ada Farms",
  phone: "08012345678",
  notes: "Prefers morning deliveries",
  status: "ACTIVE",
};

const contextValue = {
  error: "",
  formatMoney: value => `NGN ${value}`,
  farm: { currency: "NGN" },
  batches: [{ id: "batch-1", code: "L-2026-001" }],
  expenseForm: { occurredDate: "", description: "", amountMinor: "", category: "", batchId: "" },
  setExpenseForm: vi.fn(),
  expenses: [],
  expenseBatchFilter: "",
  setExpenseBatchFilter: vi.fn(),
  customers: [customer],
  customerForm: { name: "", phone: "", notes: "" },
  setCustomerForm: vi.fn(),
  emptyCustomer: { name: "", phone: "", notes: "" },
  emptyExpense: { occurredDate: "", description: "", amountMinor: "", category: "", batchId: "" },
  editingCustomerId: "",
  editCustomer: vi.fn(),
  cancelCustomerEdit: vi.fn(),
  sales: [
    {
      id: "sale-older",
      customerId: "customer-1",
      customerName: "Ada Farms",
      saleDate: "2026-10-20",
      saleType: "EGG",
      quantity: 10,
      unit: "crate",
      unitPriceMinor: 2500,
      totalAmountMinor: 25000,
      batchId: "batch-1",
    },
    {
      id: "sale-newer",
      customerId: "customer-1",
      customerName: "Ada Farms",
      saleDate: "2026-11-02",
      saleType: "BROILER",
      quantity: 2,
      unit: "birds",
      unitPriceMinor: 150000,
      totalAmountMinor: 300000,
      batchId: "batch-1",
    },
    {
      id: "unlinked-sale",
      customerId: "another-customer",
      customerName: "Ada Farms",
      saleDate: "2026-11-03",
      saleType: "EGG",
      quantity: 1,
      unit: "crate",
      unitPriceMinor: 1000,
      totalAmountMinor: 1000,
      batchId: "batch-1",
    },
  ],
  saving: false,
  expenseCategories: [{ id: "general", name: "General" }],
  loadExpenses: vi.fn(),
  saveCustomer: vi.fn(),
  saveExpense: vi.fn(),
};

function renderCustomers() {
  return render(
    <NavigationContext.Provider value={{ activePage: "sales-money", activeTab: "customers" }}>
      <FarmManagerContext.Provider value={contextValue}>
        <SalesMoneyPage />
      </FarmManagerContext.Provider>
    </NavigationContext.Provider>
  );
}

describe("SalesMoneyPage contextual customer details", () => {
  it("shows customer purchase summaries and deeper sales history without repeating table notes", () => {
    renderCustomers();

    const table = screen.getByRole("table", { name: "Customers" });
    expect(within(table).getByText("2")).toBeInTheDocument();
    expect(within(table).getByText("NGN 325000")).toBeInTheDocument();
    expect(within(table).getByText("2026-11-02")).toBeInTheDocument();
    expect(within(table).queryByText("Prefers morning deliveries")).not.toBeInTheDocument();

    fireEvent.click(within(table).getByRole("button", { name: "View" }));

    const drawer = screen.getByRole("dialog", { name: "Ada Farms" });
    expect(within(drawer).getByText("Prefers morning deliveries")).toBeInTheDocument();
    expect(within(drawer).getByText("Total sales")).toBeInTheDocument();
    expect(within(drawer).getByText("Total amount spent")).toBeInTheDocument();
    expect(within(drawer).getByText("Most recent purchase")).toBeInTheDocument();
    expect(within(drawer).getByRole("heading", { name: "Sales history" })).toBeInTheDocument();
    expect(within(drawer).getByText("2026-11-02 · Broiler birds")).toBeInTheDocument();
    expect(within(drawer).getByText("2026-10-20 · Eggs")).toBeInTheDocument();
    expect(within(drawer).queryByText("2026-11-03 · Eggs")).not.toBeInTheDocument();
  });

  it("opens customer editing from the drawer action", () => {
    renderCustomers();
    const table = screen.getByRole("table", { name: "Customers" });
    fireEvent.click(within(table).getByRole("button", { name: "View" }));
    const drawer = screen.getByRole("dialog", { name: "Ada Farms" });

    fireEvent.click(within(drawer).getByRole("button", { name: "Edit customer" }));

    expect(contextValue.editCustomer).toHaveBeenCalledWith(customer);
    expect(screen.queryByRole("dialog", { name: "Ada Farms" })).not.toBeInTheDocument();
  });
});
