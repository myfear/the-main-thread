#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime="$root/.runtime/mflux"

if [[ "$(uname -s)" != Darwin || "$(uname -m)" != arm64 ]]; then
    echo "This inference runtime requires an Apple Silicon Mac." >&2
    exit 1
fi
if (( $(sw_vers -productVersion | cut -d. -f1) < 26 )); then
    echo "The locked MLX Metal package requires macOS 26 or later." >&2
    exit 1
fi
if ! command -v uv >/dev/null 2>&1; then
    echo "Install uv first: brew install uv" >&2
    exit 1
fi
export UV_CACHE_DIR="$root/.runtime/uv-cache"
export UV_PYTHON_INSTALL_DIR="$root/.runtime/python"
uv venv --allow-existing --python 3.12 "$runtime"
uv pip sync --require-hashes --python "$runtime/bin/python" "$root/inference/requirements.txt"
printf '\nMFLUX executable: %s/bin/mflux-generate-flux3\n' "$runtime"
