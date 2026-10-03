# Release

A release is a tested Windows application, not merely a successful compile.

## 1. Prepare the release

Update the application version consistently:

- `package.json`
- `backend/pom.xml`

Review migrations and ensure every schema change has a new forward-only migration.

Review the changelog/release notes if one is maintained.

## 2. Run quality gates

From a clean working tree:

```powershell
npm install
npm test
npm run lint
npm run backend:verify
npm run build
```

Do not mark a gate as passed without command output from the current source revision.

## 3. Package the application

Set JDK 17:

```powershell
$env:JAVA_HOME="C:\Path\To\JDK-17"
java -version
```

Then:

```powershell
npm run package
```

Packaging performs:

1. `npm run package:runtime`
2. `npm run build`
3. `npm run backend:package`
4. `electron-builder`

The runtime preparation uses `jlink` and creates the `runtime/` directory. Electron Builder then includes the backend JAR and runtime as extra resources.

The Windows installer is emitted under:

```
release/
```

## 4. Release validation

### Fresh installation

On a clean Windows environment:

1. Install the NSIS installer.
2. Launch the application.
3. Confirm the backend becomes healthy.
4. Confirm first-time account setup works.
5. Confirm the database is created under the user's application-data directory.
6. Confirm normal farm operations work.

### Upgrade

Use an existing database:

```
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

### Backup and restore

Before release:

1. Create a manual backup.
2. Validate the backup.
3. Confirm automatic backup settings behave as configured.
4. Restore the backup on a test installation.
5. Verify records, settings and schema after restore.
6. Verify recovery if backend restart fails.

### Realistic farm validation

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

## 5. Security validation

Confirm:

- backend binds to `127.0.0.1`
- renderer has no Node integration
- context isolation is enabled
- preload exposes only required IPC
- arbitrary navigation is blocked
- no production database is inside the installer
- no credentials or secrets are committed
- no required CDN dependency exists

## 6. Installer inspection

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

## 7. Publish

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
- [ ] Electron build passes
- [ ] Bundled Java runtime generated
- [ ] Windows installer generated
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
