# Phase 41: CI

## Goal

Every push to `main` and every pull request targeting `main` runs the automated quality gates required before release.

The workflow is:

```text
Frontend:
  npm ci
  npm run lint
  npm test
  npm run build

Backend:
  mvnw compile
  mvnw test
  mvnw verify

Electron:
  npm run package
```

## GitHub Actions

Workflow: `.github/workflows/ci.yml`

The workflow uses:

- Node.js 20
- Java 17 Temurin
- Maven Wrapper
- Ubuntu for frontend/backend checks
- Windows for the Electron packaging check

The Electron job waits for both the frontend and backend jobs. This matters because the production package expects the built renderer, backend JAR, and packaged Java runtime to be available.

## Pull request enforcement

CI passing is not, by itself, a merge restriction. GitHub branch protection must require these checks on `main`:

- `Frontend`
- `Backend`
- `Electron build`

Recommended branch protection settings:

1. Require a pull request before merging.
2. Require status checks to pass before merging.
3. Select `Frontend`, `Backend`, and `Electron build` as required checks.
4. Require branches to be up to date before merging.
5. Require conversation resolution before merging.
6. Disable force pushes to `main`.
7. Restrict branch deletion for `main`.

The repository integration used while implementing this phase does not have permission to modify branch-protection settings, so the final merge restriction must be enabled by a repository administrator in GitHub Settings.

## Local reproduction

Frontend:

```powershell
npm ci
npm run lint
npm test
npm run build
```

Backend:

```powershell
mvnw.cmd -f backend\pom.xml compile
mvnw.cmd -f backend\pom.xml test
mvnw.cmd -f backend\pom.xml verify
```

Electron package:

```powershell
mvnw.cmd -f backend\pom.xml package -DskipTests
npm run package
```

## CI design rules

- CI must use committed lockfiles and `npm ci`, not `npm install`.
- CI must use the Maven Wrapper rather than depending on a globally installed Maven version.
- The workflow must fail immediately when any required command fails.
- CI must not depend on the developer's local database.
- CI must not contain production credentials or secrets.
- Electron packaging runs on Windows because Windows is the primary release target.
- The workflow has read-only repository contents permission.

## Phase 41 acceptance

- [x] Frontend dependencies install automatically.
- [x] Frontend lint runs automatically.
- [x] Frontend tests run automatically.
- [x] Frontend build runs automatically.
- [x] Backend compile runs automatically.
- [x] Backend tests run automatically.
- [x] Backend verification runs automatically.
- [x] Electron packaging runs automatically.
- [x] Pull requests trigger CI.
- [ ] GitHub branch protection requires all three CI jobs before merge.

The final unchecked item is an administrative GitHub repository setting, not a code change.
