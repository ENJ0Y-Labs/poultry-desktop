# Session cookie policy

Pomas uses a server-managed session token in the `poultry_session` cookie. The cookie policy is shared by login and logout so the browser receives a matching scope when the cookie is invalidated.

## Current contract

| Attribute | Login behavior | Logout behavior | Reason |
|---|---|---|---|
| Name | `poultry_session` | `poultry_session` | Must match the name read by the backend. |
| `SameSite` | `None` for packaged Electron / cross-origin development; `Lax` for same-origin LAN browser | Matches the login context | Same-origin LAN browser requests do not need cross-site cookies. |
| `Secure` | Present for packaged Electron / cross-origin development and HTTPS; absent for HTTP LAN | Matches the login context | Browsers reject Secure cookies on ordinary HTTP private-LAN addresses. |
| `HttpOnly` | Present | Present | Renderer JavaScript must not be able to read the session token. |
| `Path` | `/` | `/` | Makes the cookie available to the API routes and ensures logout targets the same cookie scope. |
| `Expires` / `Max-Age` | Omitted | `Max-Age=0` | Login creates a browser-session cookie. The server-side session expires after 12 hours; logout immediately expires the browser cookie. |

The packaged Electron and cross-origin development flows use `SameSite=None; Secure`. The production LAN browser workspace is same-origin and uses `SameSite=Lax`; its cookie is Secure only when the origin uses HTTPS. Do not broaden the cookie Domain or expose the token to renderer JavaScript.

## Origin trust boundary

The loopback-only packaged Electron renderer retains the literal `Origin: null` compatibility allowance. LAN mode does not: Electron switches to the same-origin browser workspace before publishing the LAN URL, and the backend rejects opaque or cross-origin requests. In both modes, `X-Requested-With` is CSRF friction rather than an authentication secret.

Treat the null-origin allowance as a loopback-only compatibility boundary. LAN mode removes `null` from the CORS allow-list and requires the request Host to match the actual bound IPv4 address and port; any supplied Origin must exactly match that same origin. State-changing LAN requests require exact Origin and the CSRF marker. Automated coverage lives in `LanHostOriginSecurityTest`; packaged Electron runtime checks remain required.

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


## Same-origin LAN browser transport (Issues #310 and #314)

A production browser workspace served by the Pomas host uses the same scheme, hostname, and port as the REST API. For that exact same-origin request only, the session cookie uses `SameSite=Lax`. On an HTTP-only private LAN URL the cookie is not marked `Secure`, because browsers reject Secure cookies on ordinary LAN HTTP origins. It remains host-only, `HttpOnly`, and `Path=/`; no `Domain` attribute is added. If the same-origin host uses HTTPS, `Secure` remains enabled.

The existing packaged Electron (`Origin: null`) and browser-development cross-origin flows retain the existing `SameSite=None; Secure` policy. An absent, opaque, malformed, or different origin is never classified as same-origin by this cookie helper.

This is session-cookie compatibility groundwork only. It does not enable a LAN listener, serve production browser assets, or replace the required LAN Host/Origin and CSRF enforcement. Those controls must be implemented and tested before LAN access can be enabled. Plain HTTP LAN access is limited to a trusted private Wi-Fi network; it is not internet hosting and does not provide transport encryption.
