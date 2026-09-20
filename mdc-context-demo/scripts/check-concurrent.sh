#!/usr/bin/env bash
set -euo pipefail

base_url=${1:-http://localhost:8080}
export base_url

check_one() {
    local id="request-$1"
    curl --fail --silent --show-error --max-time 10 \
        -H "X-Request-ID: $id" "$base_url/mdc/bridged" |
        jq -e --arg id "$id" \
            '.library.requestId == null and .continuation.requestId == $id' > /dev/null
}
export -f check_one

seq 1 100 | xargs -n 1 -P 20 bash -euo pipefail -c 'check_one "$1"' _
printf 'All 100 bridged requests kept their own IDs.\n'
