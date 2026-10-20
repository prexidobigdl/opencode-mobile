# OpenCode Mobile — Plan & Architecture

Ship an Android APK that **contains the OpenCode v2 engine** and drives it through a WebView
UI, with the upstream web app served by the engine itself on `127.0.0.1`.

- Upstream reference clone: `~/src/opencode-v2` (`anomalyco/opencode`, branch `v2`, v2.0.26, MIT)
- This project: `~/projects/opencode-mobile`
- Source of truth for upstream facts: `docs/upstream-map.md`

## 1. Key upstream facts (verified)

| Fact | Detail |
|---|---|
| Engine | TypeScript monorepo, runs on **Bun** by default, but has a full **Node build path** (`packages/cli/script/build-node.ts`) |
| Server | `opencode serve --port N --hostname H` (`packages/cli/src/commands/handlers/serve.ts`) |
| Web UI | Engine serves the SolidJS app (`@opencode/app`) over HTTP from its embedded app-archive (`packages/cli/services/web-ui.ts`) |
| HTTP API | 139 endpoints, OpenAPI spec at `packages/protocol/openapi.json` |
| Release binaries | Bun `--compile` targets incl. `linux-arm64` and `linux-arm64-musl` |
| DB | `node:sqlite` built-in → engine-on-Node needs **Node ≥ 22.5** |
| License | MIT — safe to fork/modify/bundle with attribution |

## 2. Architecture

```
┌─────────────────────────── Android app: ai.opencode.mobile ───────────────────────────┐
│                                                                                       │
│  MainActivity ── WebView(127.0.0.1:port)  ◄── upstream web UI (@opencode/app build)   │
│      │                        │  native bridges: file picker (SAF), share-in,         │
│      │                        │  "settings" for API keys → EncryptedSharedPreferences │
│      ▼                        ▼                                                       │
│  EngineService (foreground, dataSync)                                                 │
│      └── EngineRunner: spawns/hosts the engine                                        │
│             ├─ Track A: exec libopencode.so (Bun-compiled static musl binary)         │
│             └─ Track B: exec libnode.so + bundles (Node SEA/bundle, no Bun at runtime)│
│                                                                                       │
│  Engine process: opencode serve --port <random, chosen via ServerSocket(0)>           │
│      • reads/writes its config + auth in HOME=$filesDir (app-private)                 │
│      • projects opened via SAF-resolved paths or app-private workspace dir            │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

## 3. Decision: `.so` in jniLibs vs encrypted-asset → **ship as `lib*.so`**

User asked which is better. Answer: **native-lib embedding, not encryption.**

- Android guarantees the file is extracted **with exec permission** to `nativeLibraryDir`
  when named `lib*.so` + `extractNativeLibs="true"` (`useLegacyPackaging` in AGP 8).
- Encrypted-asset-then-decrypt-to-data-dir **does not work on targetSdk ≥ 29**: W^X forbids
  `execve` of files under app data dirs. Workarounds exec `memfd` via `/proc/self/fd` —
  fragile, flagged by Store heuristics, and buys nothing: anything decrypted on-device is
  extractable anyway. Net: worse everywhere; keep it as an optional hardening module only.
- Exec at `nativeLibraryDir` works for arm64 now; 32-bit support arrives with Track B
  (two ABIs in jniLibs, `libopencode.so`/`libnode.so` per ABI).

## 4. Two engine tracks (work in parallel, shared app code)

### Track A — MVP: bundled Bun-compiled musl binary (arm64)
Built **by us in CI** from a pinned upstream tag (`.github/workflows/engine.yml`), so no
upstream release-artifact naming roulette and no downstream patch needed. Zero JS tooling
at runtime; the binary just needs:
- `execve` from `nativeLibraryDir` (static musl ELF, no bionic dependencies)
- env: `HOME=$filesDir`, `TMPDIR=$cacheDir`, `PATH=$nativeLibDir:/system/bin`
- shell tool fallback: `/system/bin/sh` works; Termux tooling optional, never required
- **Goal:** end-to-end APK (engine spawns → port detected → web UI session chat works on-device)

### Track B — v1.0: Node-runtime engine (removes Bun at runtime, enables 32-bit ARM)
User requirement: no heavy Bun dependency at runtime + attempt `armeabi-v7a`. Node has
Android-bionic builds (nodejs-mobile project) for arm64 **and** armv7; Bun does not.

Work items (engine-layer fork, kept minimal via `packages/core` loadout pattern):
1. `bun run script/build-node.ts --bundle-only` in CI (Bun only needed at **build** time —
   this is exactly how the user's "Bun not available for armv7" concern is made moot).
2. Swap/rebuild glibc natives for bionic (upstream Node SEA target is 26.4.0; the full
   per-platform native list is in `packages/cli/src/node/target.ts`):

   | dep | strategy |
   |---|---|
   | `node:sqlite` | needs bionic Node ≥ 22.5 (nodejs-mobile may be older) → vendor a custom bionic Node build or replace loadout |
   | `@lydell/node-pty-*` | rebuild under NDK (pure N-API C++, feasible) or stub with error page for terminal tab |
   | `@parcel/watcher-*` | disable; verify upstream file-watching fallback |
   | `@ff-labs/fff-bin` + `@yuuang/ffi-rs` | file-search via `ProcessBuilder` (`find`/busybox) fallback — patch loadout only |
   | `opencode-pty` binary asset | drop persistent-terminal feature on mobile |
   | `photon_rs` / tree-sitter | wasm — platform-neutral, no work |

3. Ship bundle + bionic Node as `libnode.so` (arm64 + optional armv7) via the same
   jniLibs-exec model that Track A already uses; only the runner swaps.

## 5. App layer (shared across tracks)
- `EngineService`: foreground service (`dataSync`), owns lifecycle + port StateFlow,
  HTTP-readiness probe with 30s timeout (what the WebView waits on).
- Port allocation: ephemeral (`ServerSocket(0)`) → `serve --port N --hostname 127.0.0.1`.
  Never 0.0.0.0; loopback only.
- WebView: JS on, localhost devtools in debug, `networkSecurityConfig` allows cleartext
  **for 127.0.0.1/localhost only**.
- API keys (the engine's `opencode auth` flow): reroute through a native settings screen;
  keys land in `$filesDir/.local/share/opencode/` (engine-readable, app-private).
- Workspace access: SAF tree URIs resolved to real paths under `/storage/emulated/0/…`
  with persisted grants; also support an app-private "projects" folder.

## 6. Licensing / compliance
- Upstream is **MIT** (`~/src/opencode-v2/LICENSE`). We may modify, bundle, redistribute —
  condition: keep the upstream license text + copyright visible in the product.
- `third_party/LICENSING.md` lists bundled components; APK About screen carries attribution.
- Our changes stay MIT. Adding Android-native binaries must keep license purity (no GPL
  contamination) — see the Track B native-rebuild step.

## 7. Milestones

| # | Milestone | Acceptance |
|---|---|---|
| M0 | Scaffold + CI pipelines produce artifacts | `engine.yml` uploads engine binary; APK CI consumes it |
| M1 | APK shell + engine spawn cycle (Track A) | localhost engine reachable from WebView on-device |
| M2 | Chat end-to-end | prompt from WebView, streamed replies render |
| M3 | Keys + workspace picker | provider login via native screen, SAF folder grant |
| M4 | Track B spike: bionic Node build | engine boots with Node bundle on-device |
| M5 | armv7 attempt + native fallbacks | `armeabi-v7a` APK installs and engine smoke-tests |
| M6 | Hardening | per-ABI release CI, signed APK, attribution screen |

## 8. Risks

| Risk | Mitigation |
|---|---|
| musl binary not 100% static | Phase-0 check inside `engine.yml` (`readelf -l` PT_INTERP missing ⇒ static); fallback: glibc-loader trick or pivot to Track B |
| NDK-native swap effort in Track B | feature-flag loadouts, ship degraded v1.0 (no persistent PTY, no file-watcher) |
| bionic Node version too old for `node:sqlite` | maintain custom bionic Node build in CI or swap DB loadout |
| WebView vs engine API drift | pin to `packages/protocol/openapi.json`; run upstream app e2e tests in CI |
| Engine spawning shells by absolute path | `PATH` ordering: bundled busybox first, `/system/bin/sh` last |
| Engine moves fast upstream | pin tags; cherry-pick critical fixes; rebase fork monthly |

## 9. Non-goals (explicit)
- Upstream TUI (`@opencode/tui`, opentui) on mobile — unusable; UI is the served web app.
- Local model serving (llama.cpp/Ollama-in-APK) — future work; Track B's Node runtime opens that door.
- On-device (Termux) APK builds — CI-only per decision; revisit only if a PC is unavailable.
