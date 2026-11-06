#!/usr/bin/env bash
# Shared by the Bash scripts; compatible with macOS Bash 3.2.
set -euo pipefail

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || die "Install $1 and try again."; }
need_value() { [ "$#" -ge 2 ] && [ -n "$2" ] || die "Missing value for $1"; }

init_ollama() {
    require ollama
    require curl
    require jq
    OLLAMA_URL=${OLLAMA_HOST:-http://127.0.0.1:11434}
    case "$OLLAMA_URL" in
        http://*|https://*) ;;
        *) OLLAMA_URL="http://$OLLAMA_URL" ;;
    esac
    OLLAMA_URL=${OLLAMA_URL%/}
    local local_api='^https?://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$'
    [[ "$OLLAMA_URL" =~ $local_api ]] || die "OLLAMA_HOST must select a local Ollama daemon (localhost or a loopback address)."
    export OLLAMA_HOST="$OLLAMA_URL"
    api_get /api/tags >/dev/null || die "Start Ollama locally first (open the app or run ollama serve)."
}

api_get() { curl --noproxy '*' --connect-timeout 5 --max-time 30 -fsS "$OLLAMA_URL$1"; }

init_registry() {
    REGISTRY_URL=${1%/}
    local valid_url='^https?://[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]+)?$'
    [[ "$REGISTRY_URL" =~ $valid_url ]] || die "Registry must be http(s)://HOST[:PORT], without a path, credentials, or query. Use a DNS name or IPv4 address."
    REGISTRY_HOST=${REGISTRY_URL#*://}
    REGISTRY_FLAGS=()
    case "$REGISTRY_URL" in http://*) REGISTRY_FLAGS=(--insecure) ;; esac
    curl --noproxy '*' --connect-timeout 5 --max-time 15 -fsS "$REGISTRY_URL/v2/" >/dev/null || die "Cannot reach registry at $REGISTRY_URL/v2/"
}

local_digest() {
    api_get /api/tags | jq -r --arg name "$1" '
      .models[]? | select(.name == $name) | .digest |
      if startswith("sha256:") then . else "sha256:" + . end'
}

manifest_digest() {
    # GET instead of HEAD also works with registries that negotiate media types.
    local headers digest
    headers=$(curl --noproxy '*' --connect-timeout 5 --max-time 30 -fsS \
        -H 'Accept: application/vnd.docker.distribution.manifest.v2+json' \
        -D - -o /dev/null "$REGISTRY_URL/v2/${1%:*}/manifests/${1##*:}") || return 1
    digest=$(printf '%s\n' "$headers" | tr -d '\r' | awk 'tolower($1)=="docker-content-digest:" {print $2}' | tail -1)
    [[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]] || die "Registry did not return a SHA-256 manifest digest for $1"
    printf '%s\n' "$digest"
}

validate_lock() {
    [ -f "$1" ] || die "Missing lock file: $1. Run preload.sh and distribute its models.lock.json."
    jq -e '
      .schema_version == 1 and (.models | type == "array" and length > 0) and
      all(.models[];
        (.repository | type == "string" and test("^workshop/[a-z0-9][a-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*$")) and
        (.alias | type == "string" and test("^[a-z0-9][a-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*$")) and
        (.digest | type == "string" and test("^sha256:[a-f0-9]{64}$"))) and
      ([.models[].alias] | length == (unique | length)) and
      ([.models[].repository] | length == (unique | length))
    ' "$1" >/dev/null || die "Invalid or duplicate entries in lock file: $1"
}
