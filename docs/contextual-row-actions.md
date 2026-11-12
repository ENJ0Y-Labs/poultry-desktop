# Contextual row actions (Issue #236)

The shared `DataTable` accepts an optional `rowActions` callback. It receives each visible row and must return an array of action descriptors for that row.

## Action descriptor

- `id`: stable unique key within the row menu.
- `label`: short action label.
- `onSelect(row)`: invokes the existing page/context workflow for the selected record.
- `visible`: set to `false` when the action is not applicable or not available to the current user.
- `disabled` and `disabledReason`: keep an action visible but unavailable when explaining the lifecycle restriction is useful.
- `destructive`: marks an action as destructive; the caller must still use the existing confirmation workflow.

The shared `RowActionsMenu` provides a consistent three-dot trigger, menu semantics, outside-click dismissal, Escape handling, arrow-key navigation, disabled-state explanation and focus movement.

## Domain boundary

This component is presentation-only. It does not infer permissions, record status, or transaction rules. The page that owns the record must construct the allowed action list from current record state and available authorization context. Every mutation must call the existing backend-backed workflow. Spring Boot remains authoritative and must reject invalid requests even if a renderer action is hidden or disabled.

Do not expose unrestricted edit/delete for posted financial or stock transactions. Posted records remain read-only and must use the audited reversal-plus-replacement workflow. Deactivation, archive, correction and other destructive actions must use the existing confirmation UI and backend API. Never add a client-only mutation or directly alter a row object.

## Current implementation status

The shared menu primitive and optional `DataTable rowActions` integration are implemented. Existing module pages have not yet been migrated to supply domain-specific action descriptors. Backend authorization/rejection coverage and audit-trail verification remain outstanding. This is a partial implementation, not completion of Issue #236.

## Verification

Focused renderer test:

```powershell
npm test -- --run app/renderer/src/components/RowActionsMenu.test.jsx
```

Then run:

```powershell
npm test
npm run lint
npm run backend:verify
npm run build
```

Manual validation requires a disposable farm database and mock records. Verify keyboard operation, action visibility for each lifecycle state, disabled explanations, confirmation flows, posted transaction correction/reversal, backend rejection of invalid requests, and audit history. Do not use production farm data.
