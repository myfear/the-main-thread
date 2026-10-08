#!/bin/sh
set -eu
DEMO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$DEMO_ROOT/.runtime/config" "$DEMO_ROOT/output" "$DEMO_ROOT/evidence"
export PHOTOCRAFT_CONFIG_DIR="$DEMO_ROOT/.runtime/config"
exec "$DEMO_ROOT/.runtime/PhotoCraft.app/Contents/MacOS/photocraft" \
  --control 7878 \
  --control-token-file "$DEMO_ROOT/.runtime/control.token" \
  --automation-read-root "$DEMO_ROOT" \
  --automation-write-root "$DEMO_ROOT"

