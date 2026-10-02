# Decision: Layer egg production and inventory

## Context

Phase 11 adds egg production and sales for Layer batches while keeping Broiler batches out of the egg workflow.

## Decisions

1. Egg production is stored as individual eggs.
2. Good and cracked eggs are recorded separately.
3. Cracked eggs remain historical and are never sellable.
4. Good inventory is derived from good collections minus good sales.
5. The farm's configurable crate size defaults to 30 eggs.
6. A sale accepts fractional crates, but the quantity must convert exactly to a whole number of individual eggs.
7. A sale stores the crate size used at sale time so changing the farm default does not change history.
8. Money remains integer minor units.
9. Sale totals must be exact integer minor-unit amounts.
10. Egg sales are validated against the complete historical ledger, including future records, so a back-dated sale cannot create a negative later balance.
11. All egg writes are restricted to active Layer batches and are audited.

## Consequence

The UI can present familiar crate quantities while the backend remains precise at the individual-egg level. Humanity gets to keep its crates, while the database gets to keep its integers.
