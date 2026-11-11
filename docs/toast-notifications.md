# Toast notifications

Pomas uses the app-wide `ToastProvider` from `app/renderer/src/components/ToastProvider.jsx`. It is mounted once above `App` in `main.jsx`; do not create per-page providers.

## API

Use `const notify = useToast()` in a component rendered below the provider.

- `notify.success(message, options?)`
- `notify.error(message, options?)`
- `notify.warning(message, options?)`
- `notify.info(message, options?)`
- `notify.dismiss(id)`

Options accept `duration` in milliseconds (default 5,000; use `0` for persistent) and an optional stable `key` for event-level duplicate suppression. Identical notifications within the short suppression window are collapsed. The visible stack is capped at four; each item can be dismissed manually.

Routine successful actions and non-blocking warnings belong in toasts. Errors that require the operator to correct a field remain inline. Destructive confirmations, irreversible decisions, and errors requiring a decision remain in dialogs or dedicated recovery UI, never in a toast.

Messages must be concise and operator-facing. Never pass raw exception objects, SQL, stack traces, filesystem paths, or backend implementation details. Prefer mapping known backend error codes to a clear action. The provider applies a final defensive message filter, but callers remain responsible for meaningful wording.

## Accessibility and layout

Success, warning, and informational notifications use a polite live region; errors use an assertive alert. Dismiss controls have accessible names and keyboard focus styles. The viewport is fixed to the upper-right, uses pointer-events isolation so it does not block the rest of the app, adapts to narrow windows, and respects reduced-motion preferences. Keep the stack short and avoid covering primary controls.

## Current integration

The application shell mounts one provider. Farm expense, population adjustment, physical count, feed usage, mortality/culling, bird sale, egg collection, egg sale, backup creation, and backup restoration success outcomes use the shared API. Continue migrating remaining module outcomes as part of their respective changes; do not reintroduce page-local success banners.

## Verification

Run:

```sh
npm test -- --run app/renderer/src/components/ToastProvider.test.jsx
npm test
npm run lint
npm run backend:verify
npm run build
```

Manual smoke test with disposable demo data:
1. Save an expense and confirm one success toast appears.
2. Record feed usage and a mortality record; confirm each shows one toast.
3. Trigger an invalid expense save; confirm the validation message remains inline and no success toast appears.
4. Trigger a safe backend failure; confirm the operator sees a useful message without SQL or stack details.
5. Create a backup and restore a disposable backup; confirm success notifications.
6. Trigger several notifications quickly; confirm the stack remains readable, newest items appear, duplicates collapse, and each can be dismissed by mouse and keyboard.
7. Resize the window to 1100×650 and 1366×768; confirm toasts do not cover the main form submit controls or sidebar navigation.
