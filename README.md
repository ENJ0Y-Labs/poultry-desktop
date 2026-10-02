# Poultry Farm Manager

Local-first poultry farm management for layer and broiler farms.

## Architecture

Electron → React/JavaScript → REST API → Spring Boot → SQLite

## Current stage

Stage 1 — repository and farm foundation.

## Development

Requirements: Node.js/npm, Java 21, and Maven.

Install dependencies:

```bash
npm install
```

Run the desktop app and local Spring Boot backend together:

```bash
npm run dev
```

Run backend tests:

```bash
npm run backend:test
```

Build the Electron application bundle:

```bash
npm run build
```

The local backend defaults to `http://127.0.0.1:18942`.

## Configuration

Development supports `PORT`, `DATABASE_PATH`, and `LOG_LEVEL`.

Production database files will live under Electron's application-data directory and will be supplied to Spring Boot by Electron.

## Important

This repository is not production-ready yet. Stage 1 is establishing the application foundation. Read `AGENTS.md` and `docs/` before changing architecture, schema semantics, or farm rules.
