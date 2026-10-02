# Feed management decision — 2026-10-02

## Decision

Feed costing uses FIFO, first in first out.

Each feed type has a configurable name, unit, and applicable flock type: LAYER, BROILER, or BOTH.

Feed purchase facts preserve the original purchase quantity, unit, purchase date, supplier reference, and total cost. Inventory is derived from purchases minus recorded usage.

Feed usage consumes the oldest available purchase lot first. A partial lot allocation uses integer minor-unit arithmetic:

`allocated cost = remaining lot cost × quantity consumed ÷ remaining lot quantity`

The integer quotient is allocated. When a lot is fully exhausted, its remaining cost is allocated in full so no minor currency unit disappears.

## Quantity representation

Feed quantities are stored as integer thousandths of the configured unit. This supports values such as 12.5 kg without floating-point persistence.

## Accounting

A feed purchase creates:
1. feed inventory
2. exactly one FEED expense for the purchase total

Feed usage does not create another expense. Its FIFO-attributed cost is added once to the batch bird-cost pool as an additional attributable cost.

## Chronology

Feed purchases and usages cannot be backdated before an existing record for the same feed type. This is deliberate. Once a feed usage cost has been carried into bird-cost accounting, inserting an earlier purchase or usage would change that historical FIFO allocation. Rewriting historical bird-cost events would violate the append-only accounting model.

Same-day records are ordered by technical creation timestamp.

## Metrics

Batch feed cost is the FIFO-attributed cost of feed used by that batch as of a business date.

Feed cost per current bird is:

`feed cost ÷ current birds`

If there are no current birds, the value is undefined and represented by `null` in the API.

## Reconciliation

For a feed type at a date:

`purchased quantity = consumed quantity + remaining quantity`

and:

`purchased cost = consumed cost + remaining cost`

These invariants are covered by calculation and integration tests.
