#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
QUERIES="$ROOT/queries"

if ! command -v codebase-memory-mcp >/dev/null 2>&1; then
  echo "codebase-memory-mcp is not on PATH."
  exit 1
fi

echo "=== search_graph: config mapping symbols ==="
codebase-memory-mcp cli search_graph "$(cat "$QUERIES/config-mapping-discovery.json")" | head -80

echo
echo "=== query_graph: deployment + arc deployment methods ==="
codebase-memory-mcp cli query_graph "{\"query\": $(jq -Rs . < "$QUERIES/build-item-producers.cypher")}" | head -80

echo
echo "=== trace_path: ConfigMappingCreator (outbound) ==="
codebase-memory-mcp cli trace_path '{"function_name": "ConfigMappingCreator", "direction": "both", "depth": 3}' | head -80

echo
echo "=== get_architecture ==="
/usr/bin/time -p codebase-memory-mcp cli get_architecture '{}' | head -120
