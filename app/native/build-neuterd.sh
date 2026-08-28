#!/bin/bash
# Build the neuterd arm64 reboot-neuter daemon (freestanding, no libc, no NDK).
# Requires: clang + ld.lld. Produces ./neuterd next to this script. build.sh embeds it into the APK
# as base64; only re-run this if you edit neuterd.c.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
LLD="${LLD:-$(command -v ld.lld || true)}"
[ -n "$LLD" ] || { echo "!! ld.lld not found — install it (apt install lld) or set LLD=/path/to/ld.lld"; exit 1; }

clang --target=aarch64-linux-gnu -O2 -nostdlib -static -ffreestanding \
  -fuse-ld="$LLD" -Wl,-e,_start -o "$DIR/neuterd" "$DIR/neuterd.c"
file "$DIR/neuterd"
echo "built $DIR/neuterd ($(wc -c < "$DIR/neuterd") bytes)"
