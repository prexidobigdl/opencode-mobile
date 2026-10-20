# OpenCode Mobile

Android APK that embeds the **OpenCode v2 engine** (TypeScript, MIT) and adapts it to
mobile life: foreground-service engine process on a loopback port, upstream web UI in
WebView, native bridges for keys/workspace/permissions.

- **Plan:** [PLAN.md](./PLAN.md)
- **Upstream code map:** [docs/upstream-map.md](./docs/upstream-map.md)
- **Upstream reference clone:** `~/src/opencode-v2` (branch `v2`; never commit into it)
- **License:** MIT — bundled upstream; see [third_party/LICENSING.md](./third_party/LICENSING.md)

## Layout

```
opencode-mobile/
├── PLAN.md                     architecture + milestones (start here)
├── android/                    Gradle app module (see android-structure below)
├── scripts/engine/             engine build + static-check scripts
├── .github/workflows/          engine.yml (build engine artifacts), android.yml (build APK)
├── docs/upstream-map.md        pointers into ~/src/opencode-v2
└── third_party/LICENSING.md    MIT upstream compliance
```

Android tree:

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts        useLegacyPackaging=true → exec-able native libs (key!)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml
        ├── res/xml/network_security_config.xml
        ├── kotlin/ai/opencode/mobile/
        │   ├── MainActivity.kt          WebView host waiting on engine readiness
        │   ├── EngineService.kt         foreground service owning engine lifecycle
        │   └── engine/EngineRunner.kt   Track A (musl binary) + Track B (node bundle) runners
        └── jniLibs/arm64-v8a/libopencode.so   (CI injects; placeholder note inside dir)
```

## Quickstart (CI-first)

1. Fork/push this scaffold → `.github/workflows/engine.yml` builds engine artifacts from a
   pinned upstream tag; `.github/workflows/android.yml` consumes them and uploads the APK.
2. Artifacts: `engine-linux-arm64-musl` (Track A), `engine-node-bundle` (Track B), `debug-apk`.
3. Local iteration loop: install APK artifact via `adb install`; Kotlin edits go through CI
   (or `gradle` on a PC — on-device builds are an explicit non-goal for now).

## Engine tracks (details in PLAN.md §3–§4)

- **Track A (MVP):** Bun-compiled **static musl** linux-arm64 binary from upstream CI,
  shipped as `libopencode.so`, exec'd from `nativeLibraryDir`. No JS tooling at runtime.
- **Track B (v1.0):** Node-runtime engine (`packages/cli/script/build-node.ts --bundle-only`)
  + bionic Node — Bun exists only at build time, and `armeabi-v7a` becomes possible.
