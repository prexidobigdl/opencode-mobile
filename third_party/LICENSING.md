# Third-party licensing

## anomalyco/opencode (branch `v2`, v2.0.26)
- **License: MIT** (`~/src/opencode-v2/LICENSE`). Redistribution (modified or not) inside
  the APK is permitted; required: keep the license text + copyright visible in the product.
- Bundled forms:
  - `libopencode.so` (Track A) — Bun-compiled engine binary built from a pinned upstream tag
  - engine JS bundle + web-UI assets (Track B) incl. the served app archive
  - web UI (SolidJS) served on loopback — effectively re-hosted upstream code in WebView

## Rules
1. Record the exact upstream commit hash in `docs/upstream-map.md` at every engine rebuild;
   keep parity between `PLAN.md` milestone record and CI artifacts.
2. Ship upstream `LICENSE` inside the APK (`app/src/main/assets/third_party/opencode/LICENSE`)
   and expose an in-app "Third-party licenses" screen listing it.
3. Any extra native binaries added for Android (node-pty rebuilds, bionic Node, busybox…)
   must be registered here with their license + source URL before they land in jniLibs.
