# Calculations

Authoritative farm calculations live in the Spring Boot domain/calculation layer.

## Bird population

The application never stores a mutable `current_birds` value.

For a batch and business date `asOf`:

`current birds = initial birds - mortality - culling - sold + transfers in - transfers out`

The calculation is implemented once in `BirdPopulationCalculator` and reused by the population service.

### Historical calculation

Every population calculation accepts an `asOf` date. Only events whose business date is on or before that date participate.

### Validation

For every population-reducing event, the backend calculates the birds available immediately before that event date. It rejects the event when:

`quantity > available birds`

This prevents:
- mortality greater than available birds
- culling greater than available birds
- sales greater than available birds
- transfer-out greater than available birds
- negative historical populations

Back-dated events are validated against the complete ordered event history, so a new record cannot make a historical balance impossible.

### Transfers

A transfer-out reduces the source batch and a transfer-in increases the target batch. Both records are committed in one transaction.

### Reference vector

For:

`5,000 - 83 - 20 - 70 + 25 - 10`

the current population is **4,842 birds**.

Stage 6 does not yet implement feed, egg, weight, or financial calculations.


A BROILER batch is eligible for the `SOLD` terminal lifecycle only when its calculated current population is zero.

## Bird cost accounting

Money is integer minor units. For NGN:

`₦500 = 50,000 kobo`

The cost engine uses a carried-cost pool rather than storing a mutable cost per bird.

### Initial purchase

A batch starts with:

`initial carried cost = original purchase cost`

The purchase is also preserved in `bird_purchases` with quantity, supplier/source, purchase date, unit cost, and total cost.

### Attributable cost allocation

The accounting rule for the current stage is deterministic weighted-average cost.

For a population reduction:

`allocated cost = carried cost × birds leaving ÷ birds available`

The calculation is performed with integer arithmetic. The proportional quotient is kept in minor units. Any remainder remains in the carried pool. If all available birds leave, the entire remaining carried cost is allocated.

Example:

`100 birds, ₦10,000 total`

`20 sold → ₦2,000 attributable cost`

`80 remaining → ₦8,000 carried cost`

The same rule applies to mortality, culling, and transfer-out because their birds leave the source batch and must carry their attributable cost.

### Additional attributable costs

A dated additional cost is added to the carried pool before population changes on the same business date.

Example:

`100 birds, ₦10,000 carried cost`

`+ ₦2,000 attributable cost`

`20 sold → 20% of ₦12,000 = ₦2,400 sold-bird cost`

`80 remaining → ₦9,600 carried cost`

### Transfers

Transfer-out calculates the source birds' attributable cost using the same weighted-average rule. That cost is recorded as a transfer-in cost on the target batch in the same transaction as the population transfer.

### Historical cost

Cost calculations support `asOf(date)`. Only population and cost events dated on or before the requested business date participate.

### Reconciliation invariant

For a batch at a date:

`initial purchase cost + attributable additions + transfer-in cost = allocated reduction cost + carried cost`

This is tested with partial sales, additional costs, mortality/culling, transfers, historical dates, and integer-remainder cases.
