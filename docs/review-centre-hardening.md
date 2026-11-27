# Review Centre hardening

## Scope of this increment

The review workflow remains based on immutable submitted versions and the existing official domain services. This increment adds targeted conflict checks without introducing a global duplicate key.

### Official-record checks

- A DAILY_OPERATIONS report is flagged when an official daily record already exists for the same batch and business date. The check returns the existing record ID, notes, and timestamps so the Manager can inspect the record that the existing adapter may update.
- An EXPENSE report is flagged when an official expense matches the same farm, nullable batch scope, business date, amount in minor units, category, and normalized description.
- Other report types continue to use submission-level duplicate detection until their own domain-specific official-record identities and conflict semantics are implemented.
- Submission-level duplicate detection reads the immutable submitted version and resolves the business date from eventDate, recordDate, usageDate, or occurredDate.
- Warnings remain warnings. They are not silently converted into rejection, and distinct legitimate same-day activity is not globally blocked.

## Regression coverage added

ReportReviewHardeningIntegrationTest covers:

1. An exact official expense duplicate produces an overrideable warning and uses occurredDate when eventDate is absent.
2. A forced provenance-link failure after the official expense insert rolls back the expense, merge operation key, and MERGED transition.
3. Two concurrent version-checked review transitions cannot both win.
4. Invalid expense data is BLOCKED while permission and immutable-version integrity checks are recorded as PASSED.
5. A Worker cannot call Manager-only queue or decision actions.
6. An official duplicate inserted after approval is detected by the pre-merge recheck, returns the submission to FLAGGED, and creates no provenance link.

## Atomic merge guarantees

A report merge is a single Spring-managed database transaction. The official domain adapter write, `report_submission_links` provenance row, transition to MERGED, MERGED workflow event, and audit log must commit together or roll back together.

- `merge_operation_key` is the idempotency key for the complete merge. A retry after commit with the same key returns the existing merged result and does not call an official domain adapter again.
- A retry for an already-merged submission with a different key is rejected with `MERGE_IDEMPOTENCY_KEY_MISMATCH`. It must never be treated as a fresh successful merge.
- The database's unique index on `merge_operation_key` prevents one operation key from being claimed by two submissions. Reuse across submissions returns a conflict.
- `beginMerge` is a compare-and-set update constrained by submission ID, current/submitted/decision version, and APPROVED lifecycle state. Only one transaction can claim the approved version.
- A competing request that encounters a transient database lock receives `MERGE_CONCURRENCY_CONFLICT` and must retry with the same key. A request that observes a completed merge succeeds only when its key matches the committed key.
- Any failure after the official write, including provenance, lifecycle, workflow-event or audit failure, must roll back the complete transaction. A failed merge must leave the submission APPROVED, with no merge key, no provenance link and no official side effect.

Regression tests in `ReportReviewHardeningIntegrationTest` cover same-key retries, different-key rejection, simultaneous competing merge attempts, and a forced failure at the final audit write after the official record, provenance link, and MERGED event have been written.

## Verification commands

Run from the repository root:

```bash
cd backend
mvn test -Dtest=ReportReviewHardeningIntegrationTest
mvn test
```

Then run the frontend regression checks because the review workflow is cross-layer:

```bash
npm test -- --run
npm run lint
npm run build
```

Use the exact scripts in the root package descriptor if they differ. Do not claim manual desktop acceptance unless tested against a disposable database.


Run from the repository root:

```bash
cd backend
mvn test -Dtest=ReportReviewHardeningIntegrationTest,ReportSubmissionIntegrationTest
```

Then run the full backend suite:

```bash
cd backend
mvn test
```

Run the frontend and desktop regression suites because this change is backend-only but sits in a cross-layer workflow:

```bash
npm test -- --run
npm run lint
npm run build
```

Use the exact scripts in the root and backend package descriptors if they differ from these commands.

## Manual acceptance on a disposable database

1. Create an expense through the normal farm ledger with a unique description, category, amount, and business date.
2. Submit a Worker expense report with the exact same facts. Confirm it becomes FLAGGED and the check identifies the existing official expense.
3. Submit a similar expense with a different amount or description. Confirm the exact-duplicate check does not flag it solely because the date matches.
4. Create a daily record for an active batch and date, then submit DAILY_OPERATIONS for that same batch/date. Confirm the Manager can inspect the existing record ID and notes before approval.
5. Approve a unique expense submission, interrupt the merge after the official write but before provenance completion using a test-only failure injection, and confirm the expense and merge state both roll back.
6. Retry a merge after a client timeout and confirm the Review Centre displays the authoritative server state without creating a second expense.

Do not run failure injection against a live farm database.

## Explicitly not covered

This increment does not implement transaction-specific post-merge correction/reversal. That work must link the original submission and official record to a typed reversal and, when correcting rather than voiding, a separate replacement. Feed FIFO, bird population/cost, egg inventory, drug-generated expenses, and common sales effects require their owning domain services to reverse and rebuild atomically. A generic SQL/table-name dispatcher is prohibited.

Official-record comparison for health, drugs, vaccination, egg, weight, feed, mortality/culling, and sales remains open. Each requires a report-specific identity definition and tests that distinguish true duplicates from legitimate repeated activity.
