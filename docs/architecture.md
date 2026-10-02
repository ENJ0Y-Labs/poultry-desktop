# Architecture

Electron → React renderer → REST API → Spring Boot → SQLite.

Electron owns desktop lifecycle. Spring Boot owns business rules, calculations, persistence, migrations, and database lifecycle. The renderer never accesses SQLite or Node.js APIs directly.

Production Electron starts the packaged Spring Boot process and waits for `GET /api/v1/health` before opening the main window.
