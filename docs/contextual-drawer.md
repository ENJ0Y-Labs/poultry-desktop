# Contextual side drawer

Use the shared right-side drawer in `app/renderer/src/components/ContextDrawer.jsx`
when an operator needs to inspect or edit an existing record without leaving the
current list or losing its filters, selection, or scroll position.

## Interaction boundary

- Use a modal for short, focused creation and confirmation flows.
- Use a drawer for contextual inspection and editing of an existing record.
- Keep the underlying page mounted. Do not change the hash route, remount the
  table, or reset list filters when opening or closing a drawer.
- The drawer is presentation-only. API requests, validation, calculations,
  transaction rules and persistence stay in the existing page/context and
  Spring Boot layers.
- Keep domain-specific forms and workflows in their existing owners; pass their
  content into the drawer rather than adding domain rules to the primitive.

## Component contract

- `size`: `small`, `medium`, or `large`.
- `mode`: `read-only` or `editable`.
- `title`, optional `description`, `children` and optional `footer` configure
  the drawer content.
- `loading` prevents dismissal while a save is in progress.
- `error` is announced in an alert region.
- `isDirty` requires an explicit Keep editing / Discard changes decision before
  closing. Supply `onDiscard` to reset local form state.
- Escape, close button and safe backdrop dismissal are supported.
- Focus enters the drawer, Tab and Shift+Tab remain inside it, and focus returns
  to the opener on close. The page behind it cannot scroll; the drawer body
  scrolls independently.
- Only one drawer should be open at a time. Do not nest drawers or modals.

## Contextual entry points

Use record detail actions on existing lists for batches, houses/pens, customers,
suppliers, inventory items, sales, expenses and audit activity where the screen
already has the relevant record data. Tables should remain scannable summaries;
drawers should add meaningful context instead of repeating every visible column.
Do not invent fields or balances. Derive customer purchase counts, totals and
last-purchase dates from the existing common sales rows, matching customer IDs
when present and falling back to the recorded customer name for legacy rows.
Outstanding balances are omitted because the current sales data
does not expose payment or receivable state.

Inventory drawers may load the existing stock-movement history for the selected
item through the current inventory movement API. Supplier drawers may show
batches that reference the supplier as their source. Feed purchase history is
not exposed in the current Feed & Stock page data model, so it remains out of
scope. Audit drawers may display the immutable changed-fields, before and after
snapshots already returned by the audit endpoint. Keep posted sales, expenses
and audit records read-only.

## Automated checks

```powershell
npm test -- ContextDrawer
npm test -- FlocksPage
npm test -- FeedStockPage
npm test -- SalesMoneyPage
npm test -- DashboardPage
npm test
npm run lint
npm run build
```

## Manual validation

Use a disposable farm database and mock records only.

1. Open a batch, house, customer, supplier, inventory item, sale, expense or
   audit detail from its list. Confirm the drawer opens from the right and the
   list remains mounted underneath.
2. Apply a list filter and scroll the list before opening a drawer. Close it and
   confirm the filter, selection and scroll position remain unchanged.
3. Open and close with the close control and Escape. Click the backdrop only
   outside the drawer; confirm inside clicks do not close it.
4. Tab and Shift+Tab through the drawer. Confirm focus never escapes and
   returns to the exact opener after close.
5. In editable mode, change a field and attempt Escape, close, and backdrop
   dismissal. Keep editing must preserve input; Discard changes must invoke the
   supplied reset callback.
6. Simulate loading and an API error. Confirm loading blocks dismissal and the
   error remains visible and announced.
7. Add long content and verify independent drawer scrolling at 1366×768 and
   1100×650 in Light, Dark and Auto themes.
8. Repeat in the Electron window when the desktop development launcher is
   available. Do not claim runtime validation until it has been run.
