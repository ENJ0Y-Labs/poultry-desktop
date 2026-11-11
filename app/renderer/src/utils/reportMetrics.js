export function calculatePeriodProfitMinor(revenueMinor, expensesMinor) {
  if (revenueMinor == null || expensesMinor == null) return null;
  const revenue = Number(revenueMinor);
  const expenses = Number(expensesMinor);
  if (!Number.isSafeInteger(revenue) || !Number.isSafeInteger(expenses)) return null;
  return revenue - expenses;
}
