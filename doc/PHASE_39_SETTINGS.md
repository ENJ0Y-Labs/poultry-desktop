# Phase 39: Settings

## Implemented

The Settings area now covers:

- Farm information
- Crate size
- Water container sizes and default
- Feed types
- Expense categories
- Application settings
- Backup settings
- User/account profile and password

## Backend

### Farm information and calculation settings

Existing farm and farm-settings APIs remain the source of truth:

- `/api/v1/farm`
- `/api/v1/farm/settings`

Crate and water configuration changes continue to be audited.

### Expense categories

Added database-backed farm-specific categories.

- `GET /api/v1/expense-categories`
- `POST /api/v1/expense-categories`
- `POST /api/v1/expense-categories/{id}/archive`

Existing FEED, DRUGS, and OTHER categories are seeded during migration. Expenses must use an active configured category.

Archived categories remain valid historical values in existing expense rows but cannot be used for new expenses.

### Application settings

Added:

- Start page
- Date format preference

Endpoints:

- `GET /api/v1/settings/application`
- `PUT /api/v1/settings/application`

The configured start page is applied by the Electron renderer.

### Backup settings

Added persisted per-farm backup configuration:

- Automatic backup enabled/disabled
- Backup directory
- Backup interval

Endpoints:

- `GET /api/v1/settings/backup`
- `PUT /api/v1/settings/backup`

The scheduler reads persisted settings and checks hourly whether the configured interval has elapsed. The Phase 38 backup-overdue attention rule also reads the same persisted configuration.

### User/account

Added:

- View current account
- Change name/email
- Change password with current-password verification

Endpoints:

- `GET /api/v1/account`
- `PUT /api/v1/account`

Passwords continue to use the existing PBKDF2 password hasher. Account changes are audited.

## Audit rules

Configuration changes that can affect calculations or operational records are audited:

- Farm information
- Crate/water settings
- Feed type creation/archiving
- Expense category creation/archiving
- Application settings
- Backup settings
- User/account changes
- Existing pricing settings

No calculation formula was moved into the renderer.

## Migration

Added:

- `V20__create_settings_management.sql`

It creates application settings, backup settings, expense categories, seeds existing farms with defaults, and replaces the old hard-coded expense category constraint with a farm-configurable category relationship.

## Tests

Added:

- `SettingsManagementIntegrationTest`
- Settings/account API coverage
- App-test mocks for persisted settings

GitHub currently reports no CI status/workflow run for the latest commit, so tests have not been claimed as passing from the remote repository.
