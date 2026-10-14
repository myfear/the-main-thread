#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
project_dir=$(pwd -P)
executable="$project_dir/.runtime/gridcraft-cli-0.5.0-macos-universal/gridcraft-cli"
test -x "$executable" || { echo 'Run sh scripts/install-macos.sh first.' >&2; exit 1; }
mkdir -p .bob
config=.bob/mcp.json
temporary=$(mktemp .bob/mcp.json.XXXXXX)
if test -f "$config"; then
  jq --arg command "$executable" --arg cwd "$project_dir" \
    '.mcpServers.gridcraft = {command:$command,args:["mcp"],cwd:$cwd,alwaysAllow:[],disabled:false}' \
    "$config" > "$temporary"
else
  jq -n --arg command "$executable" --arg cwd "$project_dir" \
    '{mcpServers:{gridcraft:{command:$command,args:["mcp"],cwd:$cwd,alwaysAllow:[],disabled:false}}}' \
    > "$temporary"
fi
mv "$temporary" "$config"
echo 'Configured project-local GridCraft MCP in .bob/mcp.json.'
