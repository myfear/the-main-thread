#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export HF_HOME="${HF_HOME:-$root/.runtime/huggingface}"
hf="$root/.runtime/mflux/bin/hf"
if [[ ! -x "$hf" ]]; then
    echo "Run ./scripts/install-runtime.sh first." >&2
    exit 1
fi
# Same snapshot and component layout as the pinned MFLUX release.
"$hf" download black-forest-labs/flux-3-image-klein \
    --revision 8454017f9ebaada30c998fccd9f67a80c7580f68 \
    --include 'transformer/*' --include 'vae/*' --include 'text_encoder/*' \
    --include 'LICENSE.md' --include 'NOTICE.md' --include 'README.md'
printf '\nModel cache: %s\n' "$HF_HOME"
