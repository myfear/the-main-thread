#!/usr/bin/env bash
set -euo pipefail

QUARKUS_DIR="${1:-${QUARKUS_DIR:-$HOME/Projects/quarkus}}"

if [[ ! -d "$QUARKUS_DIR" ]]; then
  echo "Quarkus checkout not found at: $QUARKUS_DIR"
  echo "Clone it first, or pass the path as the first argument."
  exit 1
fi

echo "Indexing: $QUARKUS_DIR"
echo "Graph data is stored under ~/.cache/codebase-memory-mcp/ by default."
echo "This can take tens of seconds and several GB of RAM on a full Quarkus tree."

/usr/bin/time -l codebase-memory-mcp cli index_repository "{\"repo_path\": \"$QUARKUS_DIR\"}"

echo
echo "Indexed projects:"
codebase-memory-mcp cli list_projects '{}'
