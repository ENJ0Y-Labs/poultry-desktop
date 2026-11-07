# Decision: editable farm master records

## Status

Approved for issue #196 implementation on 2026-11-07.

## Scope

User-entered setup/master records may be edited through explicit Edit, Cancel, and Save workflows. This includes feed definitions, batch setup references, houses/pens, suppliers, customers, inventory item definitions, and the existing farm/application settings forms.

## Invariants

- Every permitted update is performed by Spring Boot in a transaction and writes an audit entry with before/after state.
- Existing values populate edit forms. A failed validation must leave the form values available for correction.
- The renderer refreshes the affected list and dependent dashboard data after a successful update.
- Posted transactions remain append-only. Feed purchases, feed usage, mortality, culling, sales, expenses, egg collections, and inventory movements are not edited through master-record endpoints. Corrections continue to use the approved audited reversal-and-replacement workflow.
- Batch code, batch type, placement date, and initial bird count are immutable. Setup editing is limited to house and supplier/source references on ACTIVE batches.
- A batch supplier/source cannot be changed after an original bird purchase cost has been recorded.
- A house assigned to an ACTIVE batch cannot be archived.
- Feed unit and bird-type applicability cannot change after feed purchases, feed usage, or opening balances exist. A feed unit change without history resets its kilogram conversion unless the new unit is kg, which always uses a factor of exactly 1.
- Inventory item unit cannot change after stock movements exist. Names, category, and reorder levels remain editable.
- Archived master records cannot be edited through ordinary update workflows.

## Validation

Backend request validation and domain checks are authoritative. UI restrictions are usability aids only. Duplicate names/codes, invalid farm references, archived references, incompatible historical changes, and invalid quantities are rejected without partial updates.
