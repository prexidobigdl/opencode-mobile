# Upstream code map — what matters and where

Reference clone: `~/src/opencode-v2` (`anomalyco/opencode`, branch `v2`)
**Read-only for us.** Don't commit anything inside it; Android-specific forks and patches
live in this repo (see PLAN.md Track B). Record the pinned commit hash here after the
first CI engine build, and audit against the pinned tag.

## Version pin
- Cloned 2026-10-20, `v2` branch head; package versions showed **v2.0.26** in package.json.
- Binary/web-UI bytes shipped in the APK must match this pinned ref exactly.

## Engine (what the APK actually runs)
| Purpose | Path in `~/src/opencode-v2` |
|---|---|
| CLI entry + `serve` flags (`--port`, `--hostname`, `--service`, `--stdio`) | `packages/cli/src/index.ts`, `packages/cli/src/commands/handlers/serve.ts` |
| Server process spawn plumbing | `packages/cli/src/server-process.ts` |
| HTTP server (request handlers, event feed, auth) | `packages/server/src/` |
| Embedded web UI machinery | `packages/cli/src/services/web-ui.ts`, `packages/cli/src/app-archive.ts`, `app-assets.ts` |
| **Track B** Node build | `packages/cli/script/build-node.ts` (incl. `--bundle-only`), `bundle-only app-archive`, `packages/cli/src/node/target.ts` ⚠ enumerates ALL per-platform natives |
| Platform loadouts (bun vs node select) | `packages/core/package.json` `exports` conditions + `packages/core/src/database/sqlite.node.ts`, `pty/pty.node.ts`, `filesystem/fff.node.ts`, `shell/parser-wasm.*.ts`, `image/photon-wasm.*.ts` |
| Bun compile targets (Track A artifact) | `packages/cli/script/build.ts` — target list includes `linux-arm64-musl` |

## UI (what the WebView loads)
| Purpose | Path |
|---|---|
| SolidJS app shell | `packages/app/src/` (`session/`, `home/`, `composer/`, `shell/`) |
| Shared chat components | `packages/session-ui/` |
| API contract (source of truth, 139 endpoints) | `packages/protocol/openapi.json`; audit doc: `V2_HTTP_API_AUDIT.md` |
| Generated client/SDK | `packages/client/`, `packages/sdk/` |
| Theme | `packages/theme/` |

## Release plumbing (for CI wiring)
| Purpose | Path |
|---|---|
| Installer + artifact naming | `install` (repo root) |
| Release workflows | `.github/workflows/deploy-files.yml`, `script/publish.ts` |
| Versioning | `.changeset/` |

## Notes for the mobile port
- Runtime deps relevant to Android embedding are all listed in
  `packages/cli/src/node/target.ts` (`node-pty`, `parcel/watcher`, `fff-bin`, `ffi-rs`,
  `opencode-pty` asset, plus wasm assets) — Track B forks live there minimally.
- `node:sqlite` usage in `packages/core/src/database/sqlite.node.ts` ⇒ bionic Node must be
  ≥ 22.5 (upstream SEA build pins Node **26.4.0**).
- Engine already serves the web UI from its app-archive over HTTP; WebView targets
  `http://127.0.0.1:<port>` and the engine does the rest.
