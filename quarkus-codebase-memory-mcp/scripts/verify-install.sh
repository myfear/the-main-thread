#!/usr/bin/env bash
set -euo pipefail

if ! command -v codebase-memory-mcp >/dev/null 2>&1; then
  if [[ -x "$HOME/.local/bin/codebase-memory-mcp" ]]; then
    echo "codebase-memory-mcp is installed at ~/.local/bin but not on PATH."
    echo 'Add: export PATH="$HOME/.local/bin:$PATH"'
    exit 1
  fi
  echo "codebase-memory-mcp is not on PATH."
  echo "Install with:"
  echo '  curl -fsSL https://raw.githubusercontent.com/DeusData/codebase-memory-mcp/main/install.sh | bash'
  exit 1
fi

echo "codebase-memory-mcp: $(command -v codebase-memory-mcp)"
codebase-memory-mcp cli list_projects '{}' | head -20
