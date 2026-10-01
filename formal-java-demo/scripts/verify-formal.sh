#!/usr/bin/env bash
set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/formal-common.sh"

run_tlc InventoryFixed

command -v lake >/dev/null 2>&1 ||
    fail 'Lean verification requires lake on PATH. Install Lean through elan (https://lean-lang.org/install/) and add "$HOME/.elan/bin" to PATH.'

printf 'Running Lean proofs: lake build\n'
cd "$PROJECT_ROOT/formal/lean"
lake build
