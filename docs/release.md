# Release

A release is a tested Windows application, not merely a successful compile.

## 1. Supported workflows

Run these commands from the repository root.

### Development

Use Electron with the Vite development server and hot reload:

```powershell
npm run dev
```

For browser-only UI development, run the local Spring Boot backend and Vite browser server together:

```powershell
npm run dev:browser:all
```

The browser-only workflow serves the renderer at `http://127.0.0.1:5173`. Its Vite client and HMR connections are development-only. A browser audit of this URL audits the development server, not the installed desktop package.

### Production build

Build the Electron main process, preload, and renderer without starting a Vite server:

```powershell
npm run build
```

The build runs automated checks against `dist/renderer`. It fails if the production CSP differs from the expected policy, if Vite client/HMR/server references are present, or if the generated HTML points to missing renderer assets.

### Windows installer

Use JDK 17, then build the complete installer:

```powershell
$env:JAVA_HOME="C:\Path\To\JDK-17"
java -version
npm run package
npm run installer:verify
```

The installer is written to `release/PomasSetup.exe`. The package command prepares the bundled Java runtime, builds and verifies the renderer, packages the Spring Boot backend, checks production inputs, and runs Electron Builder.

## 2. Prepare the release

Update the application version consistently:

- `package.json`
- `backend/pom.xml`

Review migrations and ensure every schema change has a new forward-only migration.

## 3. Run quality gates

From a clean working tree:

```powershell
npm ci
npm test
npm run lint
npm run backend:verify
npm run build
```

Do not mark a gate as passed without command output from the current source revision.

The Windows CI job builds/packages the Electron application and verifies the installer. A successful build proves artifact checks passed; it does not replace the installed-app smoke test below.

## 4. Packaged-app smoke test

Test the installed app, not only the local Vite browser tab.

1. Run `npm run package` and `npm run installer:verify`.
2. Install `release/PomasSetup.exe`.
3. Disconnect from the internet to confirm normal operation has no external dependency.
4. Launch Pomas from the Start Menu or desktop shortcut.
5. Confirm the backend starts and the dashboard loads.
6. Navigate between pages and test theme switching and charts.
7. Open DevTools for the installed app and inspect Console and Network.
8. Confirm requests go to the local API at `127.0.0.1:18942`, with no requests to port 5173, `/@vite/client`, or a Vite HMR WebSocket.
9. Confirm there are no CSP violations, missing JavaScript/CSS assets, or blank screens.
10. Close and reopen the app; verify it starts without manually starting Vite or Java.

A browser session at `127.0.0.1:5173` is expected to contain Vite development resources and is not evidence of a packaging defect. Conversely, an installed app that requires a running Vite server fails this smoke test.

## 5. Fresh installation

On a clean Windows environment:

1. Install the NSIS installer.
2. Launch the application.
3. Confirm the backend becomes healthy.
4. Confirm first-time account setup works.
5. Confirm the database is created under the user's application-data directory.
6. Confirm normal farm operations work.

## 6. Upgrade

Use an existing database:

```text
existing application data
        ↓
install new version
        ↓
start application
        ↓
Flyway migration
        ↓
health check
        ↓
verify existing records
```

Test both a normal migration and a deliberately failing migration scenario where practical.

## 7. Backup and restore

Before release:

1. Create a manual backup.
2. Validate the backup.
3. Confirm automatic backup settings behave as configured.
4. Restore the backup on a test installation.
5. Verify records, settings and schema after restore.
6. Verify recovery if backend restart fails.

## 8. Realistic farm validation

Use realistic Layer and Broiler datasets containing:

- multiple houses
- multiple batches
- daily records
- feed purchases and usage
- egg collections and sales
- broiler weights and sales
- expenses
- inventory movements
- historical dates

Compare important totals against independently calculated expected values.

## 9. Security validation

Confirm:

- backend binds to `127.0.0.1`
- renderer has no Node integration
- context isolation is enabled
- preload exposes only required IPC
- arbitrary navigation is blocked
- no production database is inside the installer
- no credentials or secrets are committed
- no required CDN dependency exists
- built renderer CSP contains only `default-src 'self'`, `style-src 'self'`, `script-src 'self'`, and `connect-src 'self' http://127.0.0.1:18942`
- packaged renderer contains no Vite dev server, HMR client, WebSocket, wildcard local port, or unsafe CSP directive

For the packaged-app smoke test, inspect Console and Network. If a style is blocked, fix the specific resource rather than weakening production CSP globally.

## 10. Installer inspection

Inspect the generated package and verify it contains:

- Electron application files
- renderer build
- `backend/poultry-backend.jar`
- bundled `runtime`

It must not contain:

- `project/data/poultry.db`
- real farm data
- development backups
- credentials
- private keys

## 11. Publish

Only after all gates pass:

1. Commit the release changes.
2. Tag the release with the chosen version.
3. Push the tag.
4. Build the final installer from that tagged source.
5. Preserve the installer checksum and release notes.
6. Publish the Windows installer through the project's chosen distribution channel.

The release source revision must be identifiable from the published version.

## Release gate checklist

- [ ] Version updated consistently
- [ ] Frontend tests pass
- [ ] Frontend lint passes
- [ ] Backend verification passes
- [ ] Electron production build passes renderer/CSP artifact checks
- [ ] Bundled Java runtime generated
- [ ] Windows installer generated
- [ ] Installed app starts without port 5173 or a manual backend launch
- [ ] Installed renderer has no Vite/HMR references or CSP errors
- [ ] Fresh installation tested
- [ ] Upgrade tested
- [ ] Flyway migration tested
- [ ] Backup creation tested
- [ ] Backup validation tested
- [ ] Restore tested
- [ ] Realistic Layer scenario checked
- [ ] Realistic Broiler scenario checked
- [ ] Reports/CSV/PDF checked
- [ ] Security checks completed
- [ ] No database/secrets/real farm data committed
