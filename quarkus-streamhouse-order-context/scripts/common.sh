#!/usr/bin/env bash
# Shared HTTP calls; source from the two experiments.
set -euo pipefail
BASE_URL=${BASE_URL:-http://localhost:8080}
ORDER_ID=${ORDER_ID:-1042}
[[ "$ORDER_ID" =~ ^[1-9][0-9]*$ ]] || { echo 'ORDER_ID must be a positive decimal integer' >&2; exit 1; }
command -v jq >/dev/null || { echo 'Install jq before running the experiments' >&2; exit 1; }

post() {
    curl --fail-with-body --silent --show-error --max-time 15 \
        -H 'Content-Type: application/json' --data "$2" "$BASE_URL/_demo/$1"
}

context() {
    curl --fail --silent --show-error --max-time 5 "$BASE_URL/orders/$ORDER_ID/context"
}

await_context() {
    local expression=$1
    local deadline=$((SECONDS + 45))
    local result
    while (( SECONDS < deadline )); do
        if result=$(context 2>/dev/null) && jq -e "$expression" <<< "$result" >/dev/null; then
            jq . <<< "$result"
            return 0
        fi
        sleep 1
    done
    echo "Context did not satisfy: $expression" >&2
    context >&2 || true
    return 1
}

heartbeat() {
    local timestamp
    timestamp=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
    post heartbeat "$(jq -nc --arg source "$1" --arg time "$timestamp" '{source:$source,emittedAt:$time}')"
}
