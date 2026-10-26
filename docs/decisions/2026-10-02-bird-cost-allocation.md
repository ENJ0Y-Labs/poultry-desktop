# Decision: Bird cost allocation rule

## Status

Accepted for Stage 7.

## Decision

Bird costs use a deterministic weighted-average carried-cost pool.

For a population reduction:

`allocated cost = carried cost × birds leaving ÷ birds available`

The result is calculated with integer arithmetic only.

Batch purchase totals are authoritative integer minor-unit amounts and do not need to divide evenly by the initial bird count. The recorded per-bird unit cost is the integer quotient (total purchase cost divided by bird count); it is a base unit-cost field, not a replacement for the exact total. The original full purchase total remains in the batch and bird-purchase total-cost fields and seeds the carried-cost pool.

If a proportional population-reduction result is not a whole minor unit, the integer quotient is allocated to the birds leaving and the remainder stays in the carried pool. If all available birds leave, the entire remaining carried cost is allocated. This ensures the final allocation reconciles to the exact original purchase total without floating-point arithmetic.

## Ordering

On a business date, attributable cost additions are applied before population changes on that date.

Population reductions include:
- sale
- mortality
- culling
- transfer-out

A transfer-out carries its calculated source cost into the target batch as a transfer-in cost in the same transaction.

## Why

The product does not store individual bird identities. A deterministic pool is therefore needed to make attributable cost explainable without inventing per-bird records.

This rule preserves the core invariant:

`initial purchase cost + attributable additions + transfer-in cost = allocated reduction cost + carried cost`

The implementation uses Java integer arithmetic and BigInteger for intermediate multiplication so large values cannot silently overflow before the final long result.
