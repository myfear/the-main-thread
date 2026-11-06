#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
source "$SCRIPT_DIR/common.sh"

usage() {
    cat <<'EOF'
Usage: setup-participant.sh --registry http(s)://HOST[:PORT] [--lock FILE] [--replace-existing]

Downloads only from the workshop registry into your local Ollama daemon.
Verifies manifest digests against models.lock.json, then creates local aliases.
An existing alias with different contents is preserved unless --replace-existing is set.
EOF
}

registry=
lock="$ROOT_DIR/models.lock.json"
replace=false
while [ "$#" -gt 0 ]; do
    case "$1" in
        --registry) need_value "$@"; registry=$2; shift 2 ;;
        --lock) need_value "$@"; lock=$2; shift 2 ;;
        --replace-existing) replace=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "Unknown argument: $1" ;;
    esac
done
[ -n "$registry" ] || die "Pass --registry, for example http://192.168.1.20:5002"
require jq
validate_lock "$lock"
init_ollama
init_registry "$registry"

# Check every remote tag and existing alias before downloading anything.
while IFS=$'\t' read -r repository alias expected; do
    actual=$(manifest_digest "$repository")
    [ "$actual" = "$expected" ] || die "Registry tag $repository has changed. Ask the organizer for the matching lock file."
    existing=$(local_digest "$alias")
    if [ -n "$existing" ] && [ "$existing" != "$expected" ] && ! $replace; then
        die "Local alias $alias already contains a different model. Use --replace-existing to replace that alias."
    fi
done < <(jq -r '.models[] | [.repository,.alias,.digest] | @tsv' "$lock")

while IFS=$'\t' read -r repository alias expected; do
    if [ "$(local_digest "$alias")" = "$expected" ]; then
        printf 'Already ready: %s\n' "$alias"
        continue
    fi
    remote="$REGISTRY_HOST/$repository"
    printf '\nDownloading %s\n' "$remote"
    ollama pull "${REGISTRY_FLAGS[@]+${REGISTRY_FLAGS[@]}}" "$remote"
    [ "$(local_digest "$remote")" = "$expected" ] || die "Downloaded manifest digest mismatch for $remote; local alias was not changed."
    ollama cp "$remote" "$alias"
    [ "$(local_digest "$alias")" = "$expected" ] || die "Could not verify local alias $alias"
    printf 'Ready: %s\n' "$alias"
done < <(jq -r '.models[] | [.repository,.alias,.digest] | @tsv' "$lock")
printf '\nWorkshop models are installed locally.\n'
