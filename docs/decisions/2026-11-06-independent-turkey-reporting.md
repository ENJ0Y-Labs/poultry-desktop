# Decision: independent Turkey reporting

Date: 2026-11-06

## Context

Turkey is an independent meat-production bird type. It shares the operational and weight/sale workflows with Broilers, but its population, feed use, growth, mortality, sales, and financial results must remain attributable to Turkey batches rather than being folded into Broiler totals.

## Reporting contract

- Farm dashboard exposes `totalBirds`, `layerBirds`, `broilerBirds`, and `turkeyBirds` as independently calculated populations.
- Farm summary reports expose `broilerGrowth` for BROILER batches only and `turkeyGrowth` for TURKEY batches only.
- `meatBirdGrowth` remains the combined BROILER + TURKEY collection for consumers that intentionally need all meat-bird records.
- Layer egg production remains restricted to LAYER batches. Turkey and Broiler batches must be rejected by the backend egg-management service.
- Batch reports preserve the batch's actual type in the production payload and return its weight records through the shared meat-bird production service.

## Verification

`TurkeySupportIntegrationTest` exercises independent batch creation, mortality, culling, Turkey feed purchases and usage, daily records, health/drug/vaccination history, weight records, meat sales, egg-collection rejection, farm dashboard totals, and separate farm-report growth collections.

## Compatibility

No existing migration is edited. The Turkey schema support is already provided by V27. This change adds a reporting distinction and regression coverage without changing Broiler or Layer business rules or existing farm data.
