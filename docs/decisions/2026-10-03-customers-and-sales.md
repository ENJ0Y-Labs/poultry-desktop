# Decision: Customers and common sales ledger

## Context

Stage 15 needs reusable customers and a common sales view without weakening the existing Layer and Broiler sale rules.

## Decision

Keep `egg_sales` and `bird_sales` as the type-specific authoritative sale records. Add farm-scoped `customers` and a common `sales` ledger.

Each new type-specific sale resolves a customer, writes its existing type-specific record, and writes one common sale row in the same transaction.

The sales UI supports two explicit paths:
- select a saved customer; the customer name and ID are taken from that record
- leave the saved-customer selector empty and enter the walk-in buyer's name

Walk-in names are matched case-insensitively within the active farm before a customer record is created. Repeated sales for the same name reuse the existing customer ID rather than creating duplicates. A blank walk-in name is invalid; sale records must remain attributable to a named buyer until the data model explicitly supports anonymous sales.

The common ledger uses:
- `CRATE` for Layer egg sales, preserving the exact crate quantity
- `BIRD` for Broiler sales

The common ledger is for shared commercial history and future reporting. It does not perform egg inventory, broiler population, cost, pricing, or SOLD calculations.

Existing sale rows are backfilled during V14 migration into customer records and the common ledger.

## Reason

A generic sale endpoint would allow callers to bypass type-specific rules. Keeping the existing write paths avoids creating a second business-rule implementation while still providing one common sales history for reporting.
