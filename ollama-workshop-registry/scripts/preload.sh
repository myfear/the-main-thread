#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
source "$SCRIPT_DIR/common.sh"

usage() {
    cat <<'EOF'
Usage: preload.sh [--registry URL] [--models FILE] [--lock FILE] [--reuse-local]

Defaults: registry http://127.0.0.1:5001, models.tsv and models.lock.json in the kit.
Pulls upstream models, pushes to workshop/<alias>, and writes a digest lock file.
--reuse-local skips upstream pulls for models already installed locally.
Run on a laptop with local Ollama; reach the upload port through an SSH tunnel.
EOF
}

registry=http://127.0.0.1:5001
models="$ROOT_DIR/models.tsv"
lock="$ROOT_DIR/models.lock.json"
reuse_local=false
while [ "$#" -gt 0 ]; do
    case "$1" in
        --registry) need_value "$@"; registry=$2; shift 2 ;;
        --models) need_value "$@"; models=$2; shift 2 ;;
        --lock) need_value "$@"; lock=$2; shift 2 ;;
        --reuse-local) reuse_local=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "Unknown argument: $1" ;;
    esac
done
require jq
[ -f "$models" ] || die "Model list not found: $models"
[ -d "$(dirname -- "$lock")" ] || die "Lock file directory does not exist."

# Parse and validate the entire list before any model mutations.
sources=(); aliases=()
source_re='^([a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*$'
alias_re='^[a-z0-9][a-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*$'
while IFS= read -r line || [ -n "$line" ]; do
    line=${line%$'\r'}
    [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
    read -r source_model alias extra <<< "$line"
    [[ "$source_model" =~ $source_re ]] || die "Use an upstream model with an explicit tag: $source_model"
    [[ "$alias" =~ $alias_re ]] && [ -z "$extra" ] || die "Expected exactly two columns, with a name:tag alias: $line"
    case "${source_model##*:}" in *cloud*) die "Select a downloadable local model, not $source_model" ;; esac
    for existing in "${aliases[@]+${aliases[@]}}"; do
        [ "$existing" != "$alias" ] || die "Duplicate alias: $alias"
    done
    sources+=("$source_model"); aliases+=("$alias")
done < "$models"
[ "${#sources[@]}" -gt 0 ] || die "Model list is empty."
init_ollama
init_registry "$registry"

temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/ollama-preload.XXXXXX")
lock_tmp=
trap 'rm -rf -- "$temp_dir"; [ -z "$lock_tmp" ] || rm -f -- "$lock_tmp"' EXIT
: > "$temp_dir/models.jsonl"
for ((i=0; i<${#sources[@]}; i++)); do
    source_model=${sources[$i]}; alias=${aliases[$i]}
    repository="workshop/$alias"
    remote="$REGISTRY_HOST/$repository"
    printf '\nPreparing %s as %s\n' "$source_model" "$repository"
    if ! $reuse_local || ! ollama show "$source_model" >/dev/null 2>&1; then
        ollama pull "$source_model"
    fi
    ollama cp "$source_model" "$remote"
    # Bash 3.2 with nounset needs this expansion for an empty array.
    ollama push "${REGISTRY_FLAGS[@]+${REGISTRY_FLAGS[@]}}" "$remote"
    digest=$(manifest_digest "$repository")
    source_digest=$(local_digest "$remote")
    [ "$digest" = "$source_digest" ] || die "Uploaded manifest differs from the local model: $remote"
    jq -n --arg source "$source_model" --arg alias "$alias" \
        --arg repository "$repository" --arg digest "$digest" \
        '{source:$source, alias:$alias, repository:$repository, digest:$digest}' >> "$temp_dir/models.jsonl"
done
lock_tmp=$(mktemp "${lock}.tmp.XXXXXX")
jq -s --arg created "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{schema_version:1, created_at:$created, models:.}' "$temp_dir/models.jsonl" > "$lock_tmp"
validate_lock "$lock_tmp"
mv -- "$lock_tmp" "$lock"
lock_tmp=
printf '\nPreload complete. Distribute %s with the participant scripts.\n' "$lock"
