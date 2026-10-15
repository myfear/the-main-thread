#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
base=${APP_URL:-http://127.0.0.1:8088}
request() {
    local expected=$1
    shift
    local actual
    actual=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$@")
    [[ "$actual" == "$expected" ]] || { echo "Expected HTTP $expected, received $actual" >&2; exit 1; }
    echo "HTTP $actual"
}
request 200 -u alice:alice-dev-only "$base/api/profile"
request 401 -u alice:wrong-password "$base/api/profile"
request 403 -u alice:alice-dev-only -X POST "$base/api/admin/export"
request 200 -u operator:operator-dev-only -X POST "$base/api/admin/export"
for attempt in 1 2 3 4 5; do
    request 401 -u alice:wrong-password "$base/api/profile"
done
jq -ce . target/siem/security-events.jsonl >/dev/null
echo "The audit file contains parseable JSON. Run ./scripts/verify-wazuh.sh to check detection and indexing."
