# API

All application endpoints are under `/api/v1`.

The API is REST over localhost. Controllers are thin and delegate business rules to services.

Core groups include farm, houses, batches, daily records, feed, health, drugs, vaccinations, eggs, weights, bird sales, customers, sales, expenses, inventory, dashboard, attention, reports, audit, authentication, and backup validation.

Responses use `{ok:true,data:...}` for successful application calls and `{ok:false,error:{code,message,details?}}` for application errors.

Authentication:
- POST /auth/setup, first-run only
- POST /auth/login
- POST /auth/logout
- GET /auth/me

Operational read endpoints:
- GET /dashboard
- GET /batches/{id}/dashboard
- GET /attention
- GET /reports/farm
- GET /reports/batches/{id}
- GET /audit
- POST /backup
- POST /backup/validate

Production requires an authenticated owner session. Development keeps authentication enforcement disabled so existing domain integration tests and local development can bootstrap the farm before the owner account is configured.
