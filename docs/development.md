# Development

## Layer boundaries

Electron owns lifecycle and desktop security.

React owns presentation and input handling.

Spring Boot owns validation, authorization, calculations, transactions, audit writes and persistence.

SQLite is local and is configured with foreign keys, WAL and FULL synchronous mode.

## Test loop

After backend changes:

```bash
npm run backend:test
```

After frontend changes:

```bash
npm test
npm run lint
```

Before packaging:

```bash
npm run backend:verify
npm run build
```

## Database rules

Never edit an applied Flyway migration.

Add a new migration for every schema change.

Never store derived bird population or inventory balances when the value can be calculated from authoritative events.

Money is integer minor units.

Audit records are append-only.

## Production data

Never use the development database as the packaged application's database.

Electron supplies the production database path under the user's application-data directory.

## Security

The packaged backend binds to localhost.

The renderer runs with context isolation and Node integration disabled.

Navigation outside the local application is blocked.

The packaged application uses a bundled Java runtime generated during packaging.
