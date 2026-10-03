# Decision: Expense system

## Context

The farm needs a simple financial expense ledger that can distinguish farm-wide costs from costs associated with a specific batch.

## Decision

Keep the existing `expenses` table and extend it with an optional `batch_id`.

Each expense stores:
- business date
- description
- integer minor-unit amount
- category
- optional batch association

Initial categories are `FEED`, `DRUGS`, and `OTHER`.

Drug records continue to create exactly one `DRUGS` expense automatically. Feed purchases continue to create `FEED` expenses. The general expense API does not replace those subsystem records.

Farm-level expenses have a null `batch_id`. Batch expenses reference a batch belonging to the active farm.

Historical expense rows are retained. No delete endpoint is introduced.

## Consequences

The application has one shared expense ledger rather than separate tables for fuel, repairs, transport, and similar categories.

Existing drug expenses are backfilled with their originating batch association during the Stage 14 migration.
