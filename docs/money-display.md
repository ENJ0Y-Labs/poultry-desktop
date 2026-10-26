# Money storage and display

## Storage and API contract

- Store monetary amounts as integer minor units. For NGN, 100 kobo equals ₦1.
- Keep integer minor-unit values in backend calculations, persistence, and API payloads.
- Do not change stored values or API contracts merely to make the interface readable.

## Renderer display contract

- Use `app/renderer/src/utils/money.js` and its `formatMoney(minor, currency)` function for every user-visible monetary value.
- The formatter converts integer minor units to currency display, uses the farm's configured currency, and shows two fractional digits.
- Missing amounts display as an em dash. Zero, negative/reversal amounts, large values, and fractional-naira values must remain readable.
- Do not render fields such as `amountMinor`, `remainingCostMinor`, or `totalAmountMinor` directly.
- Money inputs remain in naira and are converted to integer kobo with `parseNairaToKobo` before submission.

## Verification

Run the focused formatter and renderer tests:

```powershell
npm test -- app/renderer/src/utils/money.test.js app/renderer/src/App.test.jsx
```

Then run the complete frontend suite and production build:

```powershell
npm test
npm run build
```

The automated tests cover formatting edge cases and the expense-history/inventory screens. Packaged Electron runtime verification remains a separate check.
