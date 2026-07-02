# BreakFree

An open-source, self-imposed focus / app-blocker for Android. Apps and domains you
add are blocked **by default**. To get through, you open BreakFree, request a break,
wait out a grace period (friction against impulsive unblocking), and get a timed
window of access before blocking resumes automatically.

This is a functionality clone (architecture + UX flow), not a copy of anyone's
branding, icons, or code — built from scratch.

## How it works

### App blocking
An `AccessibilityService` listens for window-state-change events (fires the instant
the foreground app changes). If the new foreground app is on the blocked list and
there's no active break, a full-screen interstitial (`BlockOverlayActivity`) is
launched immediately on top of it.

### Domain blocking
A local-only `VpnService` intercepts **DNS queries only** — it does not route your
general traffic anywhere. This works via a routing trick: the VPN advertises itself
as the system DNS resolver, but only adds a route for that resolver's own IP address
(not `0.0.0.0/0`), so only DNS lookups get captured; everything else goes over the
network exactly as normal. This is the same approach used by open-source ad/DNS
blockers like DNS66 or NetGuard.

- Blocked domains resolve to a local sinkhole address.
- Everything else is forwarded to a real upstream resolver (Cloudflare's `1.1.1.1`
  by default — change `UPSTREAM_DNS` in `BreakFreeVpnService.kt` if you'd rather use
  something else) and relayed back, so normal browsing keeps working.
- For **plain HTTP** connections to the sinkhole, a minimal hand-rolled TCP responder
  (`TcpBlockPageResponder`) serves a static "this is blocked" HTML page.
- For **HTTPS** (the vast majority of the modern web), we deliberately do **not**
  attempt to fake a TLS response — that would require installing a device-wide
  trusted CA certificate and intercepting encrypted traffic, which is a much bigger
  privacy/security undertaking than a personal blocking tool should take on. HTTPS
  connections to a blocked domain just fail as a normal connection error.

### The break flow
`BreakStateManager` owns a single global break lifecycle: `NONE → GRACE → ACTIVE → NONE`.
- Requesting a break starts the grace-period countdown (configurable in Settings).
- `AlarmManager` (exact alarms, Doze-aware) fires transitions even if the app process
  has died.
- State is persisted via DataStore and re-derived from timestamps on every read, so a
  delayed alarm callback can't leave the app in a stuck state.
- In Strict Mode (on by default), a pending/active break cannot be cancelled early.

### Static asset sync
`AssetSyncWorker` is a `WorkManager` periodic job (every 12h, network-constrained)
that pulls a JSON manifest from a URL you host on GitHub Pages or any CDN — wire in
whatever you want it to control (default domain block-lists, curated app suggestions,
version info, etc). It's currently a stub; point `MANIFEST_URL` at your own hosted
file and fill in the parsing logic.

## Project structure

```
app/src/main/java/com/breakfree/app/
├── core/            BreakStateManager (the break lifecycle state machine)
├── data/
│   ├── db/          Room entities/DAOs for blocked apps & domains
│   ├── repository/  Thin wrapper over the DAOs
│   └── settings/     DataStore for user settings + persisted break state
├── service/
│   ├── BreakFreeAccessibilityService.kt   App-switch detection & blocking
│   ├── BreakFreeVpnService.kt             DNS interception & forwarding
│   ├── net/                               Raw packet parsing/building helpers
│   ├── BreakExpiryReceiver.kt             AlarmManager callback
│   └── BootCompletedReceiver.kt
├── sync/            AssetSyncWorker (periodic manifest fetch)
└── ui/              Compose screens (Home, App picker, Domain list, Settings, Overlay)
```

## Building

1. Open the project root in Android Studio (Koala+ recommended).
2. Let Gradle sync — it'll pull dependencies from Google/Maven Central.
3. Run on a device or emulator with **API 26+** (minSdk 26, target/compileSdk 35).
4. On first launch:
   - Tap "Enable" on the Accessibility card → grant BreakFree accessibility access.
   - Tap "Enable" on the VPN card → accept the system VPN confirmation dialog (this
     is standard Android UI for any local VPN; there's no external server involved).

## Known limitations / TODO for you

- **HTTPS block page**: intentionally not implemented (see above). If you want it
  anyway, look into a local CA + TLS termination — but think hard about the added
  attack surface and user trust implications first.
- **IPv6**: the VPN packet handling only parses IPv4. IPv6 DNS queries currently pass
  through unfiltered. Add IPv6 header parsing to `PacketUtils`/`BreakFreeVpnService`
  if you need full coverage.
- **TCP responder robustness**: `TcpBlockPageResponder` is a one-shot responder (no
  retransmission handling, no window scaling) — fine for a single small block page,
  not a general server.
- **AssetSyncWorker**: stub only — point it at a real manifest URL and implement the
  parsing/apply step.
- **Scheduled blocking windows** (e.g. "block Instagram after 10pm") isn't built yet;
  the data model would extend cleanly with a `BlockSchedule` entity if you want it.
- **Per-app/per-domain individual breaks** were explicitly scoped out in favor of one
  global break — revisit `BreakStateManager` if you want per-target granularity later.

## License 
GPLv2

