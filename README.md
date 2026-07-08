# BreakFree

An open-source, self-imposed focus / app-blocker for Android. Apps and domains you
add are blocked **by default**. To get through, you open BreakFree, request a break,
wait out a grace period (friction against impulsive unblocking), and get a timed
window of access before blocking resumes automatically.

This is a functionality clone (architecture + UX flow), not a copy of anyone's
branding, icons, or code — built from scratch.

## How it works

### App and Domain blocking
An `AccessibilityService` listens for window-state-change and content-change events.
- **Apps**: If the new foreground app is on the blocked list and there's no active break, a full-screen interstitial (`BlockOverlayActivity`) or overlay is shown.
- **Domains**: The service monitors the URL/address bar of common browsers (Chrome, Firefox, etc.). If a blocked domain is detected and no break is active, the overlay is shown to block access to the website.

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
that pulls a JSON manifest from a URL you host on GitHub Pages or any CDN. It's currently a stub; point `MANIFEST_URL` at your own hosted file and fill in the parsing logic.

## Project structure

```
app/src/main/java/com/breakfree/app/
├── core/            BreakStateManager (the break lifecycle state machine)
├── data/
│   ├── db/          Room entities/DAOs for blocked apps & domains
│   ├── repository/  Thin wrapper over the DAOs
│   └── settings/     DataStore for user settings + persisted break state
├── service/
│   ├── BreakFreeAccessibilityService.kt   App and Domain blocking logic
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

## Known limitations / TODO for you

- **AssetSyncWorker**: stub only — point it at a real manifest URL and implement the
  parsing/apply step.
- **Scheduled blocking windows** (e.g. "block Instagram after 10pm") isn't built yet;
  the data model would extend cleanly with a `BlockSchedule` entity if you want it.
- **Per-app/per-domain individual breaks** were explicitly scoped out in favor of one
  global break — revisit `BreakStateManager` if you want per-target granularity later.

## License 
GPLv2
