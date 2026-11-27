# Worker authorization boundaries (Issue #317)

## Security contract

A Worker is allowed to use only their own report-submission workflow, their own account, their own incident records, and their own notifications. Route visibility is presentation only. Every request must still pass the authenticated role and resource-owner checks in the Spring Boot backend.

- A Worker may create, list, read, edit, submit, and attach evidence to their own eligible report submission.
- A Worker must not read or mutate another user's submission by guessing or replacing its ID. Return the same not-found response for a missing submission and a submission owned by another user so identifiers cannot be enumerated.
- A Worker may add a discussion comment only to their own submission. The existing review-comment endpoint is intentionally shared with the author workflow, so its service-level owner check must remain in place.
- Manager Review queue, reviewer lists, review details, assignment, warning overrides, decisions, merges, and post-merge corrections remain Manager-only.
- Account administration remains Administrator-only.
- Manager/Administrator capabilities must never be granted by a client-supplied role, route, query parameter, submission ID, or assignment.
- Keep farm scope and authenticated identity sourced from the server-side session and database. Never trust user IDs supplied by the renderer for authorization.

## Regression coverage

SecurityAcceptanceIntegrationTest exercises the real Spring MVC filter/controller/service path with authentication required. It creates a submission as Worker A and attempts read, edit, submit, attachment listing, and comment access as Worker B. It also attempts direct Manager Review and Administrator endpoints as Worker B. Expected outcomes are 404 SUBMISSION_NOT_FOUND for another user's private submission and 403 ROLE_FORBIDDEN for privileged actions.

Run:

```powershell
.mvnw.cmd -f backend\pom.xml test -Dtest=SecurityAcceptanceIntegrationTest
.mvnw.cmd -f backend\pom.xml test
.mvnw.cmd -f backend\pom.xml verify
```

The test uses an isolated in-memory SQLite database. Do not use a real farm database or real Worker accounts.

## Manual acceptance

Using separate disposable Worker, Manager, and Administrator accounts:

1. Worker A creates a draft. Worker B must not see it in My Reports.
2. Copy Worker A's submission ID and try the detail, edit, submit, attachment, and comment API routes as Worker B. Each must be denied without changing the submission.
3. As Worker, call the Manager queue, reviewer list, review detail, assignment, decision, warning override, merge, and Administrator account routes directly. Each must be denied.
4. Confirm Manager can review another Worker's submission, while a Worker cannot.
5. Confirm the author can still access and update their own eligible draft and can comment on their own submission.
6. Confirm official farm records and audit history remain unchanged after every denied request.

Record manual outcomes separately. A passing automated test is not proof that the disposable-device acceptance was performed.
