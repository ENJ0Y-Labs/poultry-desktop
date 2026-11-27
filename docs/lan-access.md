# Secure opt-in LAN access (Issue #314)

## Security contract

Pomas remains bound to `127.0.0.1` unless a Manager explicitly enables LAN access from the authenticated desktop host. LAN mode must bind to one selected RFC1918 IPv4 address only. Never bind to `0.0.0.0`, `::`, a public address, a loopback address, or an address that is no longer assigned to the selected adapter.

The URL shown by the host is not published until the selected-address listener passes the backend identity health check and Electron confirms that the production browser workspace is served correctly. Disabling LAN access hides the URL and returns the backend to loopback. A failed transition must fail closed and must not change the SQLite database path or contents.

## Phase 1: network discovery

`app/main/network-interface.js` discovers configured RFC1918 IPv4 unicast addresses and validates a selected interface/address pair against a fresh OS interface snapshot. It rejects non-canonical IPv4 strings, public/loopback/link-local/unspecified/multicast/CGNAT addresses, invalid/non-contiguous masks, and subnet network/broadcast addresses.

Node's portable network-interface API does not report adapter operational state consistently on all supported platforms. Discovery is advisory, not proof that an address can be bound. The backend lifecycle must attempt the exact bind and treat bind/health failure as unavailable; it must never fall back to a wildcard or another adapter.

## Remaining implementation phases

- [x] Phase 2 foundation: serialized backend restart, exact validated bind address, identity health check, loopback recovery on LAN startup failure, and unchanged database path. Host controls and Windows runtime acceptance remain pending.
- [x] Phase 3: package the production renderer into the backend JAR as Spring static resources; the browser API client uses the current origin. The desktop host verifies the served HTML before switching the window or publishing the URL.
- [x] Phase 4 foundation: LAN mode validates the exact bound Host and same-origin Origin, requires Origin plus the existing CSRF marker for writes, and removes the opaque null origin from CORS while LAN is enabled. Same-origin LAN session cookies remain HttpOnly, host-only, and SameSite=Lax; HTTP LAN cookies are not Secure.
- [x] Phase 5 foundation: desktop IPC verifies a live MANAGER session, requires native confirmation, supports interface selection and enable/disable, transfers the desktop session cookie across the host switch, and displays the verified URL.
- [ ] Phase 6: complete automated lifecycle/HTTP/browser-session and disposable-database regression tests, run CI, and perform Windows manual acceptance.

Do not enable a LAN listener until all six phases are complete. Phase 1 alone intentionally does not expose the backend to the network.


## Runtime flow

1. Pomas starts on `127.0.0.1`; no LAN interface is bound by default.
2. A signed-in MANAGER or ADMINISTRATOR opens Settings → Network access, selects a discovered RFC1918 interface, and confirms the native warning.
3. Electron revalidates the selected adapter, restarts Spring Boot on that exact IPv4 address, and waits for the process-identity health check.
4. Electron requests `/` from that address and verifies that the production React entry point and theme bootstrap are present before moving the desktop window to the same-origin browser workspace.
5. The desktop session cookie is re-scoped by Electron main process to the LAN host. The browser app and API now share the same scheme, host, and port.
6. Disable requires a fresh MANAGER session check and native confirmation. The backend returns to loopback, Electron re-scopes the session cookie to loopback, and the desktop window returns to its local renderer.

The desktop app is the only place that can change listener state. Browser clients on the LAN can sign in and use Pomas but cannot change host networking. LAN mode is plain HTTP on a private interface; it is intended only for a trusted private network, not internet exposure. Do not port-forward this listener.

## Verification

Automated:
- `npm test -- --run app/main/network-interface.test.js app/main/backend-manager.test.js`
- `npm run lint`
- `npm run build`
- `mvnw.cmd -f backend/pom.xml test -Dtest=LanHostOriginSecurityTest,SecurityAcceptanceIntegrationTest,AuthControllerCookieTest`
- `npm run backend:verify`
- `npm run package`

Manual Windows acceptance remains required: enable from a Manager session, open the URL from a second device on the same trusted Wi-Fi, verify login/read/write/logout, reject a changed Host header and cross-origin form/fetch attempts, disable access, and verify the old LAN URL stops responding while localhost desktop access and the existing database remain intact. Use a disposable database for destructive workflow tests.
