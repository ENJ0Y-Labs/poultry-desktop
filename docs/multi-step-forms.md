# Multi-step workflow forms (Issue #240)

## Shared pattern

Use `app/renderer/src/components/MultiStepForm.jsx` for complex workflows with several dependent groups of information. Do not convert short forms into wizards.

- Pass an ordered `steps` array with a concise title, optional description, and content for each step.
- Keep every step mounted while hiding inactive panels. This preserves controlled form values and lets the final `ValidatedForm` run its complete field validation.
- Continue runs the shared client-side form validator against only the currently visible step, including required fields, number bounds/steps, dates, patterns and naira parsing, before advancing. Back never clears values.
- The final step is a review, not another data-entry screen. It should summarize every value that will be submitted and let the user go Back to correct it.
- Submit only from the review step. `ValidatedForm` owns final client validation, backend field-error mapping, pending state and duplicate-submit protection.
- Keep the existing API callback and payload contract. The backend remains authoritative for business rules, transactions, calculations and persistence.
- Put creation wizards inside `ModalForm` when cancellation must warn about unsaved work. Keep the form state in the existing page/context so closing after a failed request does not erase entered values. Reset only on successful save or confirmed discard.
- Do not add fields that the current API cannot persist. Do not introduce wizard state into the backend.

## Current coverage

### Batch registration

1. Batch information: bird type and placement date.
2. House selection: active house / pen.
3. Bird population: initial bird count and estimated/verified confidence.
4. Acquisition and costs: recorded purchase cost versus existing stock, plus optional supplier/source.
5. Review and create: full summary before the existing batch creation API call.

### Feed purchase

1. Feed details: registered feed type and purchase date.
2. Quantity and pricing: configured feed unit quantity and total cost.
3. Supplier information: optional registered supplier.
4. Review and save: full summary before the existing feed purchase API call.

Feed purchase must continue to create exactly one purchase/FIFO lot and its existing corresponding expense through the existing backend transaction. Do not create a second expense in the renderer.

## Verification

Focused tests:

```powershell
npm test -- app/renderer/src/components/MultiStepForm.test.jsx app/renderer/src/pages/FlocksPage.test.jsx app/renderer/src/pages/FeedStockPage.test.jsx
```

Full required checks:

```powershell
npm test
npm run lint
npm run backend:verify
npm run build
```

Manual mock-data checks (disposable farm/database only):

1. Open batch registration. Attempt to continue with the placement date blank, then correct it.
2. Move to house selection. Attempt to continue without a house, then choose an active house.
3. Enter a starting bird count. Go Back and confirm the date, type and house are unchanged.
4. Choose existing stock and confirm purchase cost is not required. Repeat with recorded purchase cost and enter a valid naira amount.
5. Select an optional supplier, reach review, and confirm every value is shown. Go Back, change a value, and confirm the review updates.
6. Submit once. Confirm one batch is created and the existing batch list/dashboard refreshes.
7. Open feed purchase. Select a feed type and date, then enter quantity and total cost. Verify the displayed unit matches the feed type's configured unit.
8. Continue without a supplier, review and save. Confirm one purchase is recorded, FIFO stock is updated and exactly one existing purchase expense is created.
9. Repeat with a supplier selected. Use Back to correct quantity and cost; verify values remain.
10. Attempt to close or press Escape with unsaved entries. Confirm the discard warning appears; choose Keep editing, then repeat and confirm Discard changes closes without submitting.
11. Double-click the final submit control while a request is pending. Confirm only one request is sent. Simulate an API failure and confirm entered values remain available for correction.

Record desktop runtime checks as not run unless actually performed against a disposable database.
