#!/bin/sh
set -eu
DEMO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
RUNTIME="$DEMO_ROOT/.runtime"
mkdir -p "$RUNTIME/downloads"
if [ -x "$RUNTIME/photocraft-cli-0.2.0-macos-universal/photocraft-cli" ] && [ -d "$RUNTIME/PhotoCraft.app" ]; then
  echo "PhotoCraft v0.2.0 is already unpacked in this workspace."
  exit 0
fi
curl -fL --retry 2 -o "$RUNTIME/downloads/photocraft.dmg" \
  https://github.com/storytold/photocraft/releases/download/v0.2.0/photocraft-0.2.0-macos-universal.dmg
curl -fL --retry 2 -o "$RUNTIME/downloads/photocraft-cli.zip" \
  https://github.com/storytold/photocraft/releases/download/v0.2.0/photocraft-cli-0.2.0-macos-universal.zip
ditto -x -k "$RUNTIME/downloads/photocraft-cli.zip" "$RUNTIME"
MOUNT_DIR=$(mktemp -d "${TMPDIR:-/tmp}/photocraft-techvoices.XXXXXX")
cleanup() {
  hdiutil detach "$MOUNT_DIR" >/dev/null 2>&1 || true
  rmdir "$MOUNT_DIR" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM
hdiutil attach "$RUNTIME/downloads/photocraft.dmg" -nobrowse -readonly -mountpoint "$MOUNT_DIR"
ditto "$MOUNT_DIR/PhotoCraft.app" "$RUNTIME/PhotoCraft.app"
spctl --assess --type execute -vv "$RUNTIME/PhotoCraft.app"
spctl --assess --type install -vv "$RUNTIME/photocraft-cli-0.2.0-macos-universal/photocraft-cli"
echo "PhotoCraft v0.2.0 is ready inside .runtime."

