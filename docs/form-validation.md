# Client-side form validation

## Behavior

- Forms use the shared `ValidatedForm` wrapper and `formValidation.js` helpers.
- Required fields, HTML number bounds/steps, integer/decimal constraints, email syntax, date values, maximum/minimum text lengths, and declared patterns are checked before a request is sent.
- Negative values are rejected for numeric fields without an explicit negative minimum. Domain-specific minimums and precision continue to come from each field's existing `min` and `step` attributes.
- Naira text fields are checked with the existing `parseNairaToKobo` parser. It uses integer arithmetic and returns integer kobo; the API payload and backend money contract are unchanged.
- Field errors appear beside their controls, with a form summary and focus moved to the first invalid field. Entered values are not reset on validation or API failure.
- The submit control is disabled while the request is pending, and the form blocks another submit until it settles.
- Backend Bean Validation details are mapped to matching fields when the server returns them. A server response without field details remains a form-level error.
- The backend remains authoritative for population, lifecycle, FIFO, duplicate, financial, and other domain invariants. Client checks are usability feedback, not a security or business-rule boundary.

## Phone and duplicate rules

- Customer phone is optional and has no format constraint in the current create API. Do not invent a phone-number pattern.
- Supplier phone is optional and limited to 40 characters by the current API contract. The renderer enforces the declared maximum where the field is rendered.
- Supplier email validation follows the existing API constraints; do not infer a stricter requirement for customer phone or other free-form contact fields.
- Duplicate and business-identity checks remain on the backend. Do not rely on a client-side list as the final duplicate check because it can be stale.

## Automated verification

Run focused tests:

```powershell
npm test -- app/renderer/src/utils/formValidation.test.js app/renderer/src/components/ValidatedForm.test.jsx app/renderer/src/utils/moneyInput.test.js
```

Run the complete required checks:

```powershell
npm test
npm run lint
npm run backend:verify
npm run build
```

## Manual mock-data verification

Use a disposable farm/database only. Do not reset or replace a real farm database.

1. Open each form area: Flocks, Daily Work, Feed & Stock, Health, Production, Sales & Money, and Settings.
2. Submit a form with each required field blank. Confirm a summary appears, the affected field shows an inline message, focus moves to the first invalid field, and no request is sent.
3. Enter valid values in some fields while leaving another required field invalid. Confirm the valid values remain in place after validation.
4. Try a negative bird/feed/stock/egg quantity and a value below an explicit minimum. Confirm it is rejected before submission. Test valid fractional quantities only where the existing field's step permits them.
5. Enter malformed money such as letters, more than two decimal places, or an amount too large for safe integer kobo. Confirm a field-level message. Enter a valid amount such as `1250.50` and verify the existing API request contains `125050` kobo.
6. Enter malformed email/date values where those controls exist. Confirm invalid values are blocked; valid dates and optional contact fields remain usable.
7. Submit a value that the backend rejects, such as a quantity exceeding the batch's available population or an operation that violates FIFO/lifecycle rules. Confirm the backend message remains visible, matching field details appear inline when supplied, and entered values are retained.
8. Double-click a submit button or press Enter repeatedly while a request is pending. Confirm only one request is sent and the submit control remains disabled until it settles.
9. Verify valid records still save and appear in their existing tables/drawers. Confirm posted transactions, audit behavior, and backend calculations are unchanged.

The manual runtime checks above must be recorded as not run unless actually performed in the desktop application with a disposable database.
