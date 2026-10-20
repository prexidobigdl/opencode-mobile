#!/usr/bin/env bash
# Exit 0 iff the ELF is statically linked (no PT_INTERP) — the requirement for exec'ing
# the engine binary from an APK's nativeLibraryDir (bionic has no musl/glibc loader).
#
# Needs binutils/readelf. On GitHub runners binutils is preinstalled.
# On the Termux device: pkg install binutils (optional; CI covers this).
set -euo pipefail

f="${1:?usage: check-elf-static.sh <elf>}"
readelf -l "$f" | grep -q PT_INTERP && {
  echo "FAIL: $f is dynamically linked (PT_INTERP present) — not directly exec'able from an APK" >&2
  exit 1
}
chmod +x "$f"
echo "OK: $f is static and exec'able"
