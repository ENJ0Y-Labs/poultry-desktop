# Decision: Estimated bird counts and physical population reconciliation

## Status
Approved for implementation through Issue #175 on 2026-11-06.

## Rules

- Initial batch population is immutable and is never overwritten by a physical count.
- New and migrated batches default to ESTIMATED. A batch becomes VERIFIED only after an authenticated owner records a physical count adjustment.
- The calculated population includes initial birds, mortality, culling, bird sales, paired transfers, and signed physical-count adjustments. An adjustment is a distinct event and must never be fabricated as mortality or culling.
- A physical adjustment records the previous calculated population, new physical quantity, signed difference, effective date, required reason, authenticated user, and server-generated UTC timestamp.
- A backdated adjustment is accepted only when replaying every later population event remains valid. Negative populations are rejected atomically.
- A batch can be marked ESTIMATED again only with a reason and audit record. This does not alter any population event.
- Negative count reconciliations allocate carried bird cost proportionally as an unclassified population reconciliation. Positive reconciliations increase bird quantity without inventing purchase cost.
- Farm dashboards and reports must make estimated batches and estimated bird totals visible.
- Transfers remain paired events; their net effect on the farm-wide population is zero.

## Example

Batch L001 starts with 700 birds and no confirmed reductions. Its provisional current count remains 700 with status ESTIMATED. No mortality record is invented. If a later physical count finds 680 birds, the application records an adjustment of -20 with the date, reason, owner, and timestamp. Initial population remains 700 and all historical mortality/culling/sales remain unchanged.