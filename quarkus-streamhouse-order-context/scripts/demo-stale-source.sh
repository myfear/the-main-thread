#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"

# Run demo.sh first. This script owns the heartbeat promise; the HTTP publisher
# does not generate heartbeats independently of these simulated sources.
await_context '.complete == true and .shipping.status == "DELAYED"' >/dev/null
heartbeat checkout
heartbeat inventory
heartbeat shipping
printf 'D: all sources reporting\n'
await_context '.sourceSignals.checkout == "RECENT" and .sourceSignals.inventory == "RECENT" and .sourceSignals.shipping == "RECENT"'

# Send checkout/inventory every 10 seconds. Shipping stops after the initial send.
# The default 30-second threshold should be crossed before the 50-second bound.
next_heartbeat=$((SECONDS + 10))
deadline=$((SECONDS + 50))
while (( SECONDS < deadline )); do
    if (( SECONDS >= next_heartbeat )); then
        heartbeat checkout
        heartbeat inventory
        next_heartbeat=$((SECONDS + 10))
    fi
    result=$(context)
    if jq -e '.sourceSignals.shipping == "STALE" and .sourceSignals.checkout == "RECENT" and .sourceSignals.inventory == "RECENT" and .complete == true and .shipping.status == "DELAYED" and .catchUpVerified == false' <<< "$result" >/dev/null; then
        jq . <<< "$result"
        curl --fail --silent --show-error --max-time 15 "$BASE_URL/q/health/ready" | jq .
        printf 'PASS: shipping evidence is stale while business state remains available\n'
        exit 0
    fi
    sleep 1
done
printf 'Source did not become stale within 50 seconds; use the default 30-second threshold.\n' >&2
exit 1
