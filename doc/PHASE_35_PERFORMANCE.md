# Phase 35: Performance

Phase 35 uses a repeatable realistic SQLite dataset instead of tiny fixture data.

## Dataset

```text
4 houses
4 layer batches
4 broiler batches
8,760 daily records (8 batches × 1,095 days)
6,000 feed-usage transactions
48 feed-purchase lots
6,000 egg collections
2,000 broiler weight records
400 sales
2,000 expenses
20 customers
3 years of historical dates
```

The automated baseline is `PerformanceBaselineTest`. It exercises:

- farm dashboard loading
- layer batch dashboard loading
- broiler batch dashboard loading
- farm report generation
- sales search/filtering
- customer search
- backup creation
- backup validation, which covers the restore input validation path
- SQLite query plans for the main dashboard/report/filter predicates

The performance test uses deliberately generous ceilings so it catches query regressions without pretending that a developer laptop and a low-end farm PC have identical hardware.

## Query indexes

Migration `V19__add_phase35_performance_indexes.sql` adds indexes for query patterns found in the actual services:

- `batches(farm_id, status)`
- `houses(farm_id, name)`
- `egg_collections(batch_id, record_date, good_eggs, cracked_eggs)`

Existing migrations already cover the other high-frequency paths, including batch/date population events, feed usage/purchases, daily records, sales, expenses, weight records, and inventory movements.

## Run

From the repository root:

```powershell
npm run backend:test
```

The performance test is intentionally part of the backend test suite.

For a focused run:

```powershell
& "$HOME\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd" -f backend\pom.xml -Dtest=PerformanceBaselineTest test
```

## Startup and Electron restore timing

Application startup and the complete Electron restore flow cross process boundaries, so they are not faked inside the JUnit dataset test.

Measure them on the target Windows machine during release validation:

1. Cold-start the packaged application three times and record each startup time from launch until the dashboard is interactive.
2. Use the generated Phase 35 backup and time the normal Electron restore flow from backup selection until the dashboard is usable again.
3. Record the median rather than the fastest run.
4. Repeat on a realistic low-end farm machine before release.

Do not optimize based on a stopwatch reading from a developer machine. Humans already have enough ways to lie to themselves about performance.

## Phase 35 result

Performance work is considered complete when the realistic dataset passes the automated baseline, the query plans use the intended indexes, and startup/restore timings have been measured on the target hardware.