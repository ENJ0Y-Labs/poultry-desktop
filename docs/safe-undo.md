# Safe undo and correction

## Current supported undo
The first integrated undo action is deliberately limited to the **latest effective meat-bird weight record**.
- The success toast exposes Undo for 15 seconds.
- The operator must provide a non-blank reason (maximum 500 characters).
- Spring Boot verifies the authenticated owner session, batch scope, original record, duplicate-reversal state, and that no later effective weight record exists.
- The original `weight_records` row is never deleted or rewritten.
- The reversal row and audit event are committed in the same database transaction.
- Weight history and growth calculations exclude records with a `WEIGHT_RECORD` reversal.
- Repeated attempts conflict. Earlier measurements are blocked while a later effective measurement depends on their growth baseline.

The server does not trust the toast timer. It rechecks eligibility when Undo is invoked.

## Not undo-enabled yet
Feed purchases, feed usage, opening stock, expenses, drug-created expenses, population events, transfers, egg collections, egg sales, bird sales, and inventory movements do not expose Undo in this change. They have linked financial, population, stock, FIFO, or sales-ledger effects and need dedicated atomic audited reversal/recalculation workflows. Feed usage reversal must rebuild later FIFO allocations and batch bird-cost allocations atomically, or reject the reversal.

## Verification
```sh
npm test -- --run app/renderer/src/components/ToastProvider.test.jsx
npm test
npm run lint
npm run backend:verify
npm run build
```
Manual disposable-data test: create one BROILER/TURKEY weight record, use Undo and enter a reason, verify the original row remains stored but is excluded from growth; retry and confirm conflict with one reversal/audit row. Add two weight records and confirm the earlier one is blocked. Add a new later measurement after the toast appears and confirm the stale action is rejected by the backend.
