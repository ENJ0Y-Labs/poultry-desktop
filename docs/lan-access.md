# Secure opt-in LAN access (Issue #314)

## Security contract

Pomas remains bound to `127.0.0.1` unless a Manager explicitly enables LAN access from the authenticated desktop host. LAN mode must bind to one selected RFC1918 IPv4 address only. Never bind to `0.0.0.0`, `::`, a public address, a loopback address, or an address that is no longer assigned to the selected adapter.

The URL shown by the host is not published until the selected-address listener passes the backend identity health check. Disabling LAN access hides the URL and returns the backend to loopback. A failed transition must fail closed and must not change the SQLite database path or contents.

## Phase 1: network discovery

`app/main/network-interface.js` discovers configured RFC1918 IPv4 unicast addresses and validates a selected interface/address pair against a fresh OS interface snapshot. It rejects non-canonical IPv4 strings, public/loopback/link-local/unspecified/multicast/CGNAT addresses, invalid/non-contiguous masks, and subnet network/broadcast addresses.

Node's portable network-interface API does not report adapter operational state consistently on all supported platforms. Discovery is advisory, not proof that an address can be bound. The backend lifecycle must attempt the exact bind and treat bind/health failure as unavailable; it must never fall back to a wildcard or another adapter.

## Remaining implementation phases

- [x] Phase 2 foundation: serialized backend restart, exact validated bind address, identity health check, loopback recovery on LAN startup failure, and unchanged database path. Host controls and Windows runtime acceptance remain pending.
- [ ] Phase 3: serve the production browser renderer from the same Spring Boot origin as the API.
- [ ] Phase 4: enforce exact Host/Origin validation, explicit CORS, CSRF, and session-cookie protections for LAN requests.
- [ ] Phase 5: authenticated Manager host controls, interface selection, status, and verified URL display.
- [ ] Phase 6: lifecycle, HTTP security, browser-session, and disposable-database regression tests plus Windows manual acceptance.

Do not enable a LAN listener until all six phases are complete. Phase 1 alone intentionally does not expose the backend to the network.
