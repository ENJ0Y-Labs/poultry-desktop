# Data table sorting and pagination (Issue #235)

The shared `DataTable` provides accessible ascending/descending sorting and pagination for loaded rows.

- Sortable headers expose the active direction through visible indicators and `aria-sort`.
- Sorting uses a column's `sortValue(row)` when supplied, otherwise its field value. Equal primary values use a stable record identifier (id, uuid, code, or batchCode) in ascending order regardless of primary direction.
- Page sizes are 10, 25, 50 and 100. The table shows the visible row range, total loaded count, page number, and first/previous/next/last controls.
- Filtering and sorting are applied before pagination. Changing filters or page size resets to page one; shrinking result sets clamp the current page.
- Rows without a stable identifier fall back to original input order for equal sort values. Persisted farm records should expose stable IDs.
- **Known limitation:** this shared component currently paginates rows supplied by the caller in the renderer. It does not reduce API payload size. High-volume endpoints still need bounded SQL pagination and total-count metadata before large histories can be considered scalable.

Focused test: `npm test -- --run app/renderer/src/utils/tableSorting.test.js`. Then run `npm test`, `npm run lint`, `npm run backend:verify`, and `npm run build`.
