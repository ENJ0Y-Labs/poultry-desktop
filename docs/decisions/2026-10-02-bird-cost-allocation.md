# Decision: Bird cost allocation rule

## Status

Accepted for Stage 7.

## Decision

Bird costs use a deterministic weighted-average carried-cost pool.

For a population reduction:

`allocated cost = carried cost × birds leaving ÷ birds available`

The result is calculated with integer arithmetic only.

If the proportional result is not a whole minor unit, the integer quotient is allocated to the birds leaving and the remainder stays in the carried pool. If all available birds leave, the entire remaining carried cost is allocated.

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
