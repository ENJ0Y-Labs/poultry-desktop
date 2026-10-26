# Session cookie policy

Pomas uses a server-managed session token in the `poultry_session` cookie. The cookie policy is shared by login and logout so the browser receives a matching scope when the cookie is invalidated.

## Current contract

| Attribute | Login behavior | Logout behavior | Reason |
|---|---|---|---|
| Name | `poultry_session` | `poultry_session` | Must match the name read by the backend. |
| `SameSite` | `None` | `None` | The supported renderer contexts include packaged Electron (`file://`) and the browser-only development workflow, where the renderer and API can be treated as cross-site. `None` permits the credentialed API requests required by these contexts. |
| `Secure` | Present | Present | Chromium requires `Secure` with `SameSite=None`. The API is bound to loopback; validate cookie acceptance in the supported Chromium runtime rather than weakening this attribute. |
| `HttpOnly` | Present | Present | Renderer JavaScript must not be able to read the session token. |
| `Path` | `/` | `/` | Makes the cookie available to the API routes and ensures logout targets the same cookie scope. |
| `Expires` / `Max-Age` | Omitted | `Max-Age=0` | Login creates a browser-session cookie. The server-side session expires after 12 hours; logout immediately expires the browser cookie. |

The application currently uses `SameSite=None; Secure`, not `SameSite=Strict`. Do not change it to `Strict` or `Lax` without validating all supported renderer origins and the credentialed login/API/logout round trip. Conversely, do not assume `None` works in every browser privacy configuration: third-party-cookie restrictions may block cookies in some browser contexts.

## Invalidation

Logout calls the backend session service to invalidate the server-side session, then emits a clearing cookie with the same name, `Path=/`, `SameSite=None`, `Secure`, and `HttpOnly` attributes. The clearing cookie adds `Max-Age=0`. Both operations matter: clearing a browser cookie alone must not leave the server session valid.

## Verification

Automated tests in `AuthControllerCookieTest` assert the exact login and logout `Set-Cookie` header contracts, including the absence of client-side expiry on login and the immediate expiry on logout.

The following runtime checks are still required before declaring cross-context behavior verified:

1. Development Electron: log in, call an authenticated endpoint, log out, and verify the cookie is removed and the session is rejected.
2. Packaged Electron: repeat the same round trip using the packaged app.
3. Browser-only development workflow: repeat the round trip in the supported browser and confirm credentialed requests work.
4. Inspect actual `Set-Cookie` and request-cookie headers in each context. Record browser version and whether privacy settings block cookies.

Do not claim these manual checks passed until they have been run. If a supported context rejects `Secure` cookies on the loopback API, fix the origin/transport arrangement or document a justified environment-specific policy; do not silently remove `Secure`.
