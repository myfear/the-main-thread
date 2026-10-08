#!/bin/sh
set -eu
umask 077

DEMO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CLI="$DEMO_ROOT/.runtime/photocraft-cli-0.2.0-macos-universal/photocraft-cli"
CONFIG_PATH="$DEMO_ROOT/.bob/mcp.json"

if [ ! -x "$CLI" ]; then
  echo "Run sh scripts/install-macos.sh first to unpack PhotoCraft v0.2.0." >&2
  exit 1
fi

mkdir -p "$DEMO_ROOT/.bob"
CONFIG_TEMP=$(mktemp "$DEMO_ROOT/.bob/mcp-config.XXXXXX")
trap 'rm -f "$CONFIG_TEMP"' 0
trap 'exit 1' HUP INT TERM

if [ -f "$CONFIG_PATH" ]; then
  cp "$CONFIG_PATH" "$CONFIG_TEMP"
  plutil -convert xml1 "$CONFIG_TEMP"
else
  plutil -create xml1 "$CONFIG_TEMP"
fi

if plutil -extract mcpServers raw -o /dev/null "$CONFIG_TEMP" 2>/dev/null; then
  plutil -extract mcpServers raw -expect dictionary -o /dev/null "$CONFIG_TEMP"
else
  plutil -insert mcpServers -dictionary "$CONFIG_TEMP"
fi

if plutil -extract mcpServers.photocraft raw -o /dev/null "$CONFIG_TEMP" 2>/dev/null; then
  plutil -remove mcpServers.photocraft "$CONFIG_TEMP"
fi

plutil -insert mcpServers.photocraft -dictionary "$CONFIG_TEMP"
plutil -insert mcpServers.photocraft.command -string "$CLI" "$CONFIG_TEMP"
plutil -insert mcpServers.photocraft.args -array "$CONFIG_TEMP"
for arg in mcp --bridge 127.0.0.1:7878 --control-token-file "$DEMO_ROOT/.runtime/control.token"; do
  plutil -insert mcpServers.photocraft.args -string "$arg" -append "$CONFIG_TEMP"
done
plutil -insert mcpServers.photocraft.cwd -string "$DEMO_ROOT" "$CONFIG_TEMP"
plutil -insert mcpServers.photocraft.disabled -bool false "$CONFIG_TEMP"
plutil -insert mcpServers.photocraft.alwaysAllow -array "$CONFIG_TEMP"
plutil -convert json -r "$CONFIG_TEMP"
mv "$CONFIG_TEMP" "$CONFIG_PATH"
echo "Configured the project-local PhotoCraft MCP bridge."
