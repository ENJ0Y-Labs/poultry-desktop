# API

Base prefix: `/api/v1`.

## Health

`GET /api/v1/health`

A healthy response proves all three foundation layers are available:

```json
{
  "ok": true,
  "status": "UP",
  "database": "UP",
  "schema": "VALID"
}
```

The endpoint executes a SQLite query and verifies that Flyway's schema history table and the application metadata table exist.

If the database is unavailable, the endpoint returns HTTP 503 with `database: DOWN`. If the database responds but the expected schema tables are missing, it returns HTTP 503 with `schema: INVALID`.

The backend is loopback-only. Development uses `localhost:<port>`; production uses `127.0.0.1:<port>`.

Feature endpoints will be added with their Stage 1 domain services.


## Farm management

- `POST /api/v1/farm` creates the single local farm profile.
- `GET /api/v1/farm` reads the active farm profile.
- `PUT /api/v1/farm` updates the farm profile.
- `GET /api/v1/farm/settings` reads crate and water defaults.
- `PUT /api/v1/farm/settings` updates the default crate size and configurable water-container sizes.
- `GET /api/v1/farm/houses` lists houses/pens.
- `POST /api/v1/farm/houses` creates a house/pen.
- `PUT /api/v1/farm/houses/{id}` updates or archives a house/pen.

The default egg crate size starts at 30 eggs. Water container sizes are configurable per farm. Houses/pens are archived rather than deleted when no longer active.
