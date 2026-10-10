# Constellation

A personal Android app for thoughts that don't get lost.

- **Capture** in seconds: widget, share sheet, text selection, app shortcuts, voice.
- **Branch** any thought into children, and **link** thoughts with typed edges: builds on, contradicts, inspired by, related to, merged into.
- **Resurface**: every day a few old thoughts come back (unlinked ones first). Branch, link, park (wait doubles each time) or kill each one.
- **Focus view**: one thought in the middle, its neighbors on an orbit around it.
- **Heat**: stars you keep returning to glow brighter; neglected ones fade.
- **Graveyard**: killed ideas and the reasons why, which become your filters.

Everything is stored on the phone only. Export a JSON backup from Settings.

## Build

Every push to `main` builds a signed APK with GitHub Actions and publishes it under Releases (`build-N`). The signing key is committed on purpose so each new build installs as an update and keeps your data.
