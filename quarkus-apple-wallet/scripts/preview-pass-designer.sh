#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
BASE_URL="${BASE_URL:-http://localhost:8080}"
mkdir -p target
# Each export gets its own directory so a re-run never overwrites edits in Pass Designer.
preview_dir="$(mktemp -d "$PWD/target/designer-preview.XXXXXX")"
curl -fsS -o "$preview_dir/template.zip" \
  "${BASE_URL}/wallet/membership/TMT-0042/template?name=Markus+Eisele&tier=Founding+Member"
unzip -q "$preview_dir/template.zip" -d "$preview_dir"
bundle="$preview_dir/TheMainThread.pkpasstemplate"
printf 'Pass Designer template: %s\n' "$bundle"

if [[ "${NO_OPEN:-0}" != "1" ]]; then
  open -a "Pass Designer" "$bundle"
fi
