# Pomas first-run onboarding

The authenticated first-run experience uses a seven-step wizard:

1. User profile: first/family name are prefilled from the authenticated account; edited names are saved through the existing audited account-update API; account email is shown read-only.
2. Role: the user's poultry-farming role.
3. Discovery: how the user heard about Pomas.
4. Farm setup: create the farm through the existing `POST /farm` API. Country is Nigeria by default, currency is NGN, and timezone is Africa/Lagos.
5. Poultry types: multi-select BROILERS, LAYERS, TURKEYS and OTHER POULTRY.
6. Priorities: multi-select farm-management goals.
7. Completion: explicit handoff to the dashboard.

The wizard does not create houses, batches, feed, stock, or sample records. Poultry types and priorities are onboarding preferences only; they do not create or configure operational records. Existing farm records bypass onboarding and open the normal workspace.

Progress is stored locally in the renderer under `pomas:onboarding:v1`. It is not farm ledger data and is cleared only after the user chooses GO TO DASHBOARD. If the application closes before farm creation, the wizard resumes at the saved step. If the farm was created but onboarding was not completed, the farm-created marker lets the wizard resume without issuing a second farm-create request. Farm creation remains protected by the existing backend contract; the UI never retries it automatically.

The illustration is built from local CSS shapes and system emoji. No remote image, font, script, map service, telemetry, or network dependency is introduced. The split layout follows Pomas' dark charcoal, warm gold and subtle grid visual language. The form panel uses existing theme-aware surface and text tokens.

## Verification

Run:

```powershell
npm test -- OnboardingWizard.test.jsx
npm test -- App.test.jsx
npm run lint
npm run build
```

Manual checks must use a disposable database:

1. Start with no farm. Confirm profile values are prefilled from the authenticated account and email is read-only.
2. Complete role and discovery steps; close/reopen before farm creation and confirm the current step and answers restore.
3. Create the farm once, close/reopen before finishing, and confirm the wizard resumes without attempting to create another farm.
4. Select multiple poultry types. Confirm the selections do not create batches or houses.
5. Finish onboarding and confirm the dashboard opens. Restart and confirm an existing farm bypasses onboarding.
6. Test Skip on optional preference steps, Back navigation, validation, and both Light and Dark themes.
7. Confirm no sample houses, batches, feed, inventory or financial transactions were created.
