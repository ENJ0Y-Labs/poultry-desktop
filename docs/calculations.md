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

Stage 6 does not implement egg, weight, or financial calculations.


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
\n## Stage 8: Feed management\n\n### Quantity representation\n\nFeed quantities are stored as integer thousandths of the configured feed unit. For example, 12.5 kg is stored as 12,500 thousandths of kg. This avoids floating-point quantity arithmetic while still supporting fractional quantities.\n\n### FIFO\n\nFor each feed type, purchases are ordered by purchase date and creation timestamp. Usage is ordered the same way. Each usage consumes the oldest available purchase lot first.\n\nFor a partial consumption of a lot:\n\n`allocated cost = remaining lot cost × quantity consumed ÷ remaining lot quantity`\n\nThe quotient is integer minor currency units. When a lot is exhausted, its entire remaining cost is allocated so no money disappears through repeated rounding.\n\nExample:\n\n- 20 bags at ₦8,000\n- 30 bags at ₦10,000\n- 25 bags consumed\n\nFIFO allocates the cost of all 20 bags from the first lot and 5 bags from the second lot.\n\n### Feed purchase accounting\n\nA feed purchase does two things in one transaction:\n\n1. increases derived feed inventory by the purchased quantity\n2. creates exactly one `FEED` expense for the purchase total\n\nLater feed usage does not create another expense. It only allocates the original purchase cost to consumed feed and adds that attributable cost once to the batch bird-cost pool.\n\n### Feed cost per bird\n\nBatch feed cost is the FIFO-attributed cost of feed used by that batch as of the requested date.\n\n`feed cost per current bird = feed cost ÷ current birds`\n\nIf current birds are zero, the per-bird value is undefined and the API returns `null`.\n\n### Chronology invariant\n\nFeed purchase or usage records cannot be backdated before an existing record for the same feed type. This prevents a newly inserted historical fact from changing the FIFO cost of an already-recorded feed usage whose cost has already been carried into bird-cost accounting.\n

## Daily operations calculations

Daily bird count is the authoritative BirdPopulationCalculator result as of the requested business date. Mortality and culling for a day are the quantities of those event types dated on that business date.

Daily feed is read from feed usage records for the batch and date. Feed quantities remain integer thousandths of the configured unit and are presented as decimal quantities for convenience.

Water total is calculated per recorded container entry:

total water units = container capacity units × container count

The UI must not invent a precise measured volume when workers only recorded container counts.
