# Shared controls

Pomas uses shared renderer primitives for actions and form entry. Import `Button` from `components/Button.jsx` and the field controls from `components/FormControls.jsx` rather than creating page-specific visual variants.

## Button

Supported variants are `primary`, `secondary`, `outline`, `ghost`, and `destructive`. Use `iconOnly` only with an explicit `ariaLabel`; use `loading` for pending actions. Buttons default to `type="button"` so nested forms cannot submit accidentally. Set `type="submit"` explicitly for a form submit action.

## Form controls

Available controls: `TextInput`, `NumberInput`, `CurrencyInput`, `DateInput`, `SelectInput`, `SearchInput`, `TextareaInput`, `CheckboxInput`, `RadioInput`, and `SwitchInput`. Each accepts a visible `label`, optional `hint`, `error`, and `required`; hint and error text are connected using `aria-describedby`. Continue to use the existing money-input parsing and API payload conventions for currency values. These components do not calculate or validate farm-domain values.

## Migration rules

- Replace page-specific control markup incrementally, preserving names, values, callbacks, validation, and submitted payloads.
- Do not move business validation or calculations into React.
- Keep existing theme tokens; do not add hard-coded screen-specific colors.
- Test keyboard focus, disabled/loading state, error descriptions, both themes, and supported window sizes.
