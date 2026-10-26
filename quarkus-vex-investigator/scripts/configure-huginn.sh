#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
java scripts/ConfigureHuginn.java "$PWD"
printf '%s\n' 'Wrote ignored .bob/mcp.json with pinned Huginn 0.1.1 and no automatic approvals.'
