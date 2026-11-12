# Progressive disclosure in data-entry forms

## Interaction contract

Pomas uses native HTML `details` / `summary` for optional form sections. This keeps the interaction keyboard-accessible without adding a dependency or custom disclosure state.

- Required fields and domain/accounting inputs stay visible in the initial form view.
- Optional fields are grouped under a clear summary such as **Optional details**.
- Closing a section does not unmount its children, reset React-controlled values, or change the form payload.
- Optional fields remain in the form and retain their existing validation attributes. Do not put required fields inside a collapsed section.
- Existing conditional fields remain conditional; for example, walk-in customer name is shown only when no saved customer is selected, and feed options remain filtered by batch type.
- Spring Boot remains authoritative for validation, persistence, calculations and audit. Do not add renderer-only fields that appear to save but are not supported by the API.

## Implemented in this change

- Daily Work: daily notes, feed usage reason, mortality reason/notes and culling reason/notes.
- Feed & Stock: feed supplier, optional feed product metadata and conversion details, inventory reorder level, inventory movement batch association, and supplier contact details.
- Health: vaccination notes.
- Sales & Money: customer phone/notes and expense batch association.

The primary fields remain visible, including batch, date, quantity, feed type, total purchase cost, expense description/category/amount, health condition/description/action, vaccine/dose/quantity, inventory item/movement type/reason/source, and supplier name.

## Data-model limitations intentionally not papered over

The current purchase and transaction contracts do not expose feed invoice reference, feed expiry date, purchase notes, purchase additional-cost breakdown, expense receipt attachment, vaccination manufacturer/batch number, sales payment details, or inventory supplier metadata. These must not be added as decorative or renderer-only inputs. Supporting them requires a separate backend/API/database design with audit and accounting implications.

## Verification

Run from the repository root:

```powershell
npm test -- DailyWorkPage.test.jsx
npm test
npm run lint
npm run build
```

Manual check with a disposable database:

1. Open each affected form at the supported desktop sizes (1366×768 and 1100×650).
2. Confirm only essential fields are initially visible.
3. Expand an optional section, enter a value, collapse it, and reopen it. Confirm the value remains.
4. Submit valid data and verify the existing saved record is unchanged in meaning.
5. Trigger a validation failure in a required visible field. Confirm browser/API validation still blocks the write.
6. Repeat while offline after the app and local backend are running. No internet dependency is introduced by native disclosure controls.
