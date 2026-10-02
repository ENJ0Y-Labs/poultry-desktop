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
