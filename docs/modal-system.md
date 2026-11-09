# Shared modal system

Short data-entry forms and confirmations use the shared renderer primitive in
`app/renderer/src/components/Modal.jsx`. The primitive is presentation-only:
form handlers, validation, API requests, calculations, and persistence remain in
the existing page/context and Spring Boot layers.

## Component contract

- `Modal` provides small, medium, and large dialog widths.
- `ModalForm` supplies a trigger for a short form and closes after a successful
  submit when the existing handler enters and leaves its shared saving state
  without setting an error.
- `ConfirmAction` provides an explicit confirmation step for destructive
  actions such as archiving a supplier.
- A dirty form cannot be dismissed accidentally. Escape, the close control,
  and backdrop dismissal show a discard choice with **Keep editing** and
  **Discard changes** actions.
- Focus moves into the dialog, Tab/Shift+Tab stay inside it, and focus returns
  to the element that opened it when the dialog closes.
- The page behind an open dialog cannot scroll. Dialog content scrolls within
  the available window height.
- Loading prevents dismissal and duplicate form submission. Errors are exposed
  through an alert region.
- Keep only one dialog active at a time. Do not put multi-step workflows,
  import previews, or large data tables inside a modal.

## Forms migrated

- Daily notes, feed usage, mortality, culling, and water records
- Customer and expense records
- Health, drug, and vaccination records
- Egg collection, egg sale, weight, and bird sale records
- Inventory item and stock movement forms
- Feed purchase and supplier forms
- Supplier archiving confirmation

Feed product configuration, opening-stock entry, feed conversion configuration,
batch setup, and other longer workflows remain inline until their own interaction
design is reviewed. Do not force a complex workflow into a modal merely to make
the page look shorter.

## Automated checks

Run focused modal and page tests:

```powershell
npm test -- Modal
npm test -- DailyWorkPage
npm test -- FeedStockPage
npm test -- App.test.jsx
```

Then run the repository checks:

```powershell
npm test
npm run lint
npm run build
```

## Manual validation

Use a disposable farm database. Never reset or modify a real farm database for
this test.

1. Run `npm run dev:browser:all` and open the local Pomas UI.
2. Open each migrated form from its trigger. Confirm the form fields and labels
   are unchanged and submit a valid mock record.
3. Cause a validation/API error. Confirm the dialog remains open, the error is
   announced, and entered values remain available for correction.
4. Enter data, then press Escape. Confirm the discard choice appears and the
   form remains open when **Keep editing** is selected.
5. Repeat using the close control and by clicking the backdrop. Confirm dirty
   input is not silently discarded.
6. Choose **Discard changes** and confirm the dialog closes.
7. Submit while the backend is unavailable or slow. Confirm loading is visible,
   dismissal is blocked, and repeated submission is prevented.
8. Verify focus enters the dialog, keyboard navigation stays inside it, and
   focus returns to the trigger after closing.
9. Check long content and internal scrolling at 1366×768 and the supported
   minimum window size of 1100×650.
10. Archive a test supplier and confirm the action requires an explicit
    confirmation. Verify its historical purchase references remain visible.

Browser mode validates React dialog behavior. Recheck the same supported sizes
in the Electron window when the desktop development launcher is available.
