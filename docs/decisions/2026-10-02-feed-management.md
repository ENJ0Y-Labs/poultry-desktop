# Feed management decision — 2026-10-02

## Decision

Feed costing uses FIFO, first in first out.

Each feed type has a configurable name, unit, and a non-empty set of applicable bird types: any combination of LAYER, BROILER, and TURKEY. The set is stored in `feed_type_applicability`; do not infer it from product names. Legacy `BOTH` definitions map to all three supported types.

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


## Multi-type applicability update — 2026-11-07

Feed applicability is a set, not a single selected bird type. The V32 forward-only migration creates `feed_type_applicability` and maps legacy `BOTH` to LAYER, BROILER, and TURKEY. Existing single-type definitions retain their one matching type. The migration does not rebuild or rewrite feed purchases, opening balances, usage, FIFO allocations, or expenses.

The API accepts and returns `applicableTypes`. A legacy `applicableType` request remains accepted for older clients during transition. The backend validates the set on creation, rejects an empty or unsupported set, enforces it again when recording usage, and applies the same rule during historical feed CSV preview/import. Daily Work lists only active feed types applicable to the selected batch. Farm feed inventory reports include the applicability set.

## Opening feed stock valuation — 2026-11-07

Opening stock represents feed already held by the farm or received without a purchase transaction. It is stored in the existing `feed_opening_balances` table and is not a purchase, supplier transaction, invoice, or expense. It must not create a second feed ledger.

Opening balances and purchased lots participate in one FIFO sequence ordered by effective date and then server-generated creation timestamp. An opening balance may predate a later purchase. A new opening balance is rejected if its effective date is before the latest existing feed usage or before the latest opening-balance entry for that feed type; this prevents backdated stock from changing historical usage allocations. Same-day records use creation timestamp order.

Opening stock has no historical valuation. It is represented as an unvalued FIFO lot, never assigned a zero or estimated cost. When a usage consumes any unvalued opening stock, that usage's `feedCostMinor` is `null` (unknown). It does not create a bird additional-cost event. Usage that consumes only valued purchased lots retains its calculated FIFO cost and existing bird-cost allocation behavior.

Inventory exposes opening quantity separately from purchased quantity. Remaining feed cost is `null` while any unvalued opening quantity remains; consumed feed cost is `null` if any consumed quantity was unvalued. Batch feed cost and cost per bird are `null` if any feed usage in that batch has unknown valuation. Dashboard and report views must display unknown valuation as unknown, not as zero.

Purchase expenses remain created exactly once by actual feed purchases. Opening stock creates no expense.
