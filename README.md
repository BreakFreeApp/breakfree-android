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
Leverages the Accessebility navigation for the UI, intercepts the URL bar and check the domain.
The event is triggered at several events.


## License 
GPLv2

