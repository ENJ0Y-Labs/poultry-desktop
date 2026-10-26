# Decision: Auditable correction and void workflows for farm transactions

## Status

Approved by the product owner on 2026-10-26 for implementation. This document is the required design record before any schema or application-contract change. The approved correction model is **reversal plus replacement**, never in-place editing of a posted transaction.

## Context and goals

Posted farm records can affect more than one screen or table. A feed purchase creates a feed lot and exactly one FEED expense; feed usage consumes FIFO lots and contributes cost to the batch bird-cost pool. Population events affect historical bird counts and bird-cost allocation. Egg collections and sales affect the sellable egg balance. Bird and egg sales also affect the common sales ledger and revenue reports. Expenses affect farm and batch financial totals.

A correction must therefore reverse the complete original business effect, preserve the original facts and audit trail, and apply the replacement as a separate, validated event. A generic edit endpoint or a generic compensating amount is not sufficient.

## Decision

### 1. Append-only transaction history

- A posted business record is never physically deleted or rewritten to represent a correction.
- A correction consists of a void/reversal linked to one original transaction and, when correcting rather than merely cancelling, a separate replacement transaction.
- The original row remains available for audit and detail views. Its effective status is derived from the reversal relationship, not by overwriting the original business fields.
- A void has no replacement. A correction has exactly one replacement. A replacement is itself a normal posted transaction and may only be corrected through a new, separately audited correction chain.
- Each reversal stores the original transaction type and ID, a required non-blank reason, the authenticated owner/actor ID, a UTC creation timestamp, and the replacement transaction type/ID when present. The reversal relationship is unique per original transaction. The schema must enforce referential integrity where possible and validate type/ID relationships in the domain service where polymorphic references cannot be represented by a normal foreign key.
- A reversal and its replacement are committed in one database transaction. If validation, replacement creation, recalculation, or audit insertion fails, the entire operation rolls back.
- Audit history remains append-only. The audit entry records the operation, reason, original reference, replacement reference where applicable, and relevant before/after summaries. Audit rows are not the only source of reversal state.

### 2. Actor, time, reason, and authorization

- Every correction or void requires an authenticated owner session, a reason that remains non-empty after trimming, and a server-generated UTC timestamp. The renderer cannot supply or override the actor or timestamp.
- The original transaction's business date is retained unchanged. The replacement carries the corrected business date and corrected business facts.
- A request that references a different farm, batch, transaction type, or nonexistent transaction is rejected without mutation.
- The first successful reversal wins. A repeated void/correction of the same original returns a conflict and creates no additional reversal, replacement, audit event, or financial/population effect.
- A replacement cannot refer to itself, its own reversal, or another transaction from a different correction chain. A replacement that has already been reversed cannot be reactivated by editing its original record.
- The operation is not allowed to bypass the existing domain service's flock-type, sold-batch, chronology, stock, population, egg-inventory, money, customer, supplier, and farm-scope validation.

### 3. Business-date and historical recalculation policy

- Business dates are domain dates, not UTC timestamps. The action timestamp is audit metadata and must not silently replace the business date.
- A replacement with an invalid date or a date that violates the existing subsystem's chronology rules is rejected.
- When an event is corrected, all derived balances and reports are recomputed from the effective, non-reversed transaction set in the subsystem's established deterministic order. Later-dated records remain present and are revalidated against the corrected earlier history; they must not be silently deleted or moved.
- If the correction would make a later record invalid (for example, a sale would exceed the corrected bird population, an egg sale would exceed good eggs available, or feed usage could no longer be costed from available lots), the whole correction is rejected with a domain conflict identifying the dependent record/date. The user must correct the dependent record(s) in a valid sequence rather than allowing negative stock/population or fabricating costs.
- Existing feed chronology restrictions remain in force. A correction may not insert a historical feed event that would silently change already-accounted FIFO allocation. Where a void/correction of a feed event requires FIFO and downstream bird-cost recomputation, the service must recalculate the affected feed lots and all dependent batch-cost allocations atomically; if the subsystem cannot safely rebuild the affected history, reject the operation rather than partially applying it. No mutable cost-per-bird shortcut is introduced.

### 4. Transaction-specific reversal semantics

| Transaction type | Reversal semantics and required integrity checks |
| --- | --- |
| Mortality / culling / population reduction | The original population event remains in history but is excluded from effective population events after reversal. Rebuild population chronologically, validate every later reduction and transfer, and recalculate carried bird cost in the same deterministic order. Never store or patch a mutable current-bird count. |
| Bird sale | Reverse the type-specific bird sale and its matching common sales-ledger effect as one logical operation. Rebuild population, sold lifecycle, allocated bird cost, revenue, and margin using the existing authoritative sale and cost services. Customer attribution and the original sale snapshot remain auditable. |
| Transfer in/out | Treat the source TRANSFER_OUT and target TRANSFER_IN and their carried cost as one linked operation. Reverse both sides atomically; reject a partial reversal. Revalidate both batches and all later population/cost events. |
| Feed purchase | Reverse the purchase lot and its single associated FEED expense together. Recompute remaining inventory and FIFO lot costs for the feed type, then rebuild dependent feed usage costs and batch bird-cost events. Feed usage must not create a second FEED expense. |
| Feed usage | Reverse the usage event and its FIFO allocations, then recompute later usages against the remaining lots and rebuild affected batch bird-cost allocations. Reject if any later usage cannot be satisfied or any dependent history cannot be deterministically rebuilt. |
| Egg collection, including cracked eggs | Reverse the collection from the effective egg ledger. Good and cracked quantities remain distinct; cracked eggs never become sellable. Validate all later sales against reconstructed good-egg inventory. |
| Egg sale | Reverse the egg-specific sale and its matching common sales-ledger row atomically. Preserve the stored crate-size snapshot and customer attribution. Rebuild egg inventory and validate all later sales. |
| Broiler sale / other bird-sale path | Reverse the authoritative type-specific sale and common sales row together, then rebuild population and cost allocations through the existing service. Do not create a second independent population or revenue implementation. |
| Expense | Reverse the expense from active expense totals without deleting the original. If it is the expense generated by a feed purchase or drug record, the owning subsystem's linked transaction must be reversed as a single operation; the general expense workflow must not orphan or double-reverse it. |
| Drug-created expense | The drug record and its generated DRUGS expense are one logical operation for correction/void purposes. Preserve the treatment record and audit history; reverse both together so the expense cannot be left behind or duplicated. |

Other operational records may be added to this workflow only when their domain side effects and invariants are explicitly covered. This design does not authorize a generic reversal endpoint that accepts arbitrary table names or bypasses domain services.

### 5. Reporting and read behavior

- Operational totals, stock balances, bird populations, egg inventory, sales, expenses, revenue, margins, and exports exclude reversed business effects.
- History/detail/audit views continue to show the original transaction with its original facts, actor/time, reason, and a clear voided/corrected status, plus links to the reversal and replacement.
- Reversal records and replacement records are visible in audit history. A voided transaction is not presented as an active transaction in forms that feed new calculations.
- Reports must use the same backend calculation services as dashboards and must not independently infer reversal behavior in the renderer.
- Reversals are not ordinary negative expenses or fake sale rows unless the existing subsystem's domain model explicitly requires a compensating ledger row. The reversal relationship is the authoritative indicator that the original effect is no longer active.

### 6. Persistence and API design constraints

- Add a new forward-only Flyway migration. Never edit a shipped migration.
- Prefer an explicit reversal/correction record with constrained status and relationship fields over ad-hoc flags spread across transaction tables. Preserve current primary keys and existing foreign-key relationships.
- Add explicit, typed domain-service operations for the supported transaction families. Controllers stay thin; no generic SQL/table-name dispatch is exposed to the API.
- Request contracts include a required reason and, for corrections, replacement facts. Actor identity and action time are server-owned.
- Database writes, dependent-ledger changes, recalculation, and audit entries must share the same Spring transaction boundary.
- Error responses distinguish invalid input, not found, already reversed, chronology/dependent-ledger conflict, and internal failure without leaking SQL or stack traces.

## Required implementation test matrix

1. Correct and void each supported transaction type; original facts remain unchanged and visible.
2. Reject missing/blank reasons, invalid dates, malformed replacement facts, wrong farm/batch/type, and unauthenticated requests.
3. Reject repeated voids and concurrent attempts without duplicate effects.
4. Correct an earlier event with later-dated records and prove all later events are revalidated; reject when a later event would become invalid.
5. Prove reversal plus replacement is atomic by forcing failure after reversal creation and verifying no partial records, totals, or audit entries remain.
6. Feed purchase/usage cases: FIFO lot order, remaining quantity/cost reconciliation, exactly one FEED expense per active purchase, and no double expense on usage.
7. Bird population: initial birds minus effective mortality, culling and sold events plus transfers in minus transfers out; no negative population on any historical date.
8. Bird cost: initial purchase cost plus attributable additions and transfer-in cost equals allocated reduction cost plus carried cost, using integer minor-unit arithmetic.
9. Eggs: effective good collections minus effective good sales never goes negative; cracked eggs remain unsellable; stored crate size and customer snapshots are preserved.
10. Sales: type-specific sale and common sales ledger are reversed together; revenue, margin and customer history reconcile.
11. Expenses: linked feed/drug-generated expenses cannot be independently reversed or duplicated.
12. Farm and batch isolation: a correction cannot affect another farm or unrelated batch.
13. Verify audit history is append-only and records actor, UTC action time, reason, original and replacement references.
14. Run backend tests, frontend tests, lint and production build; manually inspect reports and detail views against the same fixtures.

## Rollout and scope

The implementation must be delivered through a pull request and remain unmerged until required CI checks pass. Because this feature crosses current Stage 16 inventory and existing population, feed, production and accounting domains, the owner-approved issue/design authorizes this specific work; unrelated later-stage features remain out of scope.

Implementation should be split into reviewable commits where possible: design record first, migration/domain workflow second, transaction-family integration third, and tests/UI/report integration last. If implementation discovers that a transaction family cannot be reversed safely with current source data, stop and document the limitation rather than inventing a balancing entry.
