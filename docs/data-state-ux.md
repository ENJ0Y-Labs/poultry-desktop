# Data retrieval and empty-state UX

## Shared component

Use `app/renderer/src/components/EmptyState.jsx` for page-level states and `DataTable.jsx` for table states. The shared component supports these distinct variants:

- `no-records`: a valid successful response returned no records; explain the first valid action.
- `no-search-results`: records exist, but the search term matches none; provide a clear-search action.
- `no-filter-results`: records exist, but the selected filters match none; provide a clear-filters action.
- `not-applicable`: the selected batch or module type does not support this data.
- `loading`: a request is in progress; do not render the empty-records message at the same time.
- `error`: retrieval failed; retain existing data where possible and provide a retry action.

The table component resolves states in this order: retrieval error, loading, not applicable, filtered no-results, then first-use no-records. Existing callers can keep using `emptyMessage`; it becomes the explanatory text for a first-use table state. Pass `filtered` and `onClearFilters` for a filtered empty result, and pass `loading`, `error`, and `onRetry` when the owning data loader exposes those states.

## Product rules

- A successful empty response is not an error.
- A failed request is never represented as an empty response.
- Missing or undefined measurements remain unknown; do not convert them to zero.
- Do not seed sample/demo records to make an empty screen look populated.
- Only offer actions that exist and are authorized for the current user.
- Preserve existing dashboard first-run guidance; this shared component does not replace or duplicate it.
- Use semantic `status` announcements for loading and ordinary empty states, and `alert` for retrieval failures.
- Keep the actions keyboard accessible and respect reduced-motion preferences.

## Verification

Run focused tests:

```powershell
npm test -- EmptyState.test.jsx
npm test -- ProductionTrendChart.test.jsx
npm test -- DailyWorkPage.test.jsx
npm test -- FeedStockPage.test.jsx
npm test -- HealthPage.test.jsx
npm test -- ProductionPage.test.jsx
npm test -- SalesMoneyPage.test.jsx
npm test -- ReportsPage.test.jsx
```

Then run the repository checks:

```powershell
npm test
npm run lint
npm run backend:verify
npm run build
```

Manual checks must use a disposable SQLite database, never a real farm database. For each of Flocks/Houses, Daily Work, Feed & Stock, Health, Production, Sales & Money, and Reports, verify successful-empty, filtered-no-results, not-applicable, loading, and failed-read states. Confirm retry reloads the same module and that no existing record is lost or replaced. Verify keyboard navigation and the supported Electron window sizes (1100×650 and 1366×768) in Auto, Light, and Dark themes. Record runtime checks as not run until actually performed.
