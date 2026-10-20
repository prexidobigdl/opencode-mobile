#!/usr/bin/env bash
# Build the OpenCode v2 engine as a static-musl linux-arm64 binary (Track A artifact).
#
# Intended to run in CI (see .github/workflows/engine.yml). Running it locally needs:
#   - bun (build-time only; the produced binary needs no runtime tooling)
#   - node deps of the upstream monorepo (bun install)
#
# Usage:
#   OPENCODE_SRC=~/src/opencode-v2 ./scripts/engine/build-musl-binary.sh
#
# The upstream script supports pinning: only the v2 branch state matters at the tag
# we record in docs/upstream-map.md. Verify the built ELF with the companion
# check-elf-static.sh BEFORE embedding it in the APK (PLAN.md §8 risk #1).
set -euo pipefail

OPENCODE_SRC="${OPENCODE_SRC:-$HOME/src/opencode-v2}"
CLI_DIR="$OPENCODE_SRC/packages/cli"

cd "$CLI_DIR"

if ! command -v bun >/dev/null 2>&1; then
  echo "error: bun is required at build time (pkg/CI) — engine runs WITHOUT bun" >&2
  exit 1
fi

bun install
# Target name comes from targetName() in build.ts: "opencode-linux-arm64-musl".
# --skip-install avoids re-resolving deps inside the package.
bun run script/build.ts --target=linux-arm64-musl --skip-install

echo
echo "Artifact: $CLI_DIR/dist/opencode-linux-arm64-musl"
echo "Next: $(dirname "$0")/check-elf-static.sh <artifact>"
