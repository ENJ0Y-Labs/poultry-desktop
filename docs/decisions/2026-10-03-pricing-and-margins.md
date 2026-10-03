# Pricing and margin decision — 2026-10-03

## Decision

Broiler pricing is calculated from the authoritative carried bird-cost ledger.

The system keeps two farm-level margin references:

- target margin: operator-configured percentage, initially null
- working/latest margin: initially null and only increases when an actual sale meets or exceeds the configured target

## Pricing formula

For a proposed sale quantity:

1. Calculate the attributable bird cost for that quantity on the sale business date.
2. Calculate the required total selling price for the target margin.
3. Divide by the proposed quantity to obtain the backend-calculated price per bird.
4. Monetary results remain integer minor units.

Target price:

`price = actual cost / (1 - target margin / 100)`

The required total price is rounded upward to the next whole minor unit.

## Below-target sales

When a target margin exists and a proposed sale produces a margin below that target:

- the backend rejects the sale without explicit confirmation
- the target margin is not changed
- the UI asks the operator to confirm
- the confirmed sale is audited

The working margin is not increased by a below-target sale.

## Audit

Target changes, working-margin increases, and confirmed below-target sales are written to the audit log. A separate pricing margin history table preserves the pricing-specific change history.

## Source of truth

The renderer never calculates authoritative cost, margin, or selling price. Spring Boot owns the pricing calculation and validation.
