#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"

# Fixed identifiers make retries of these sample facts safe. Use a fresh broker
# for another full run; experiment A intentionally requires an unseen order.
checkout=$(jq -nc --argjson id "$ORDER_ID" '{eventId:"74c6c857-4dd0-4e05-bdc0-577ba374f7d2",orderId:$id,sku:"keyboard-1",quantity:1,placedAt:"2026-10-27T13:10:00Z"}')
inventory=$(jq -nc --argjson id "$ORDER_ID" '{eventId:"e77a0a6f-48bb-408d-b7b8-f477b6ab91b7",orderId:$id,revision:1,status:"RESERVED",occurredAt:"2026-10-27T13:11:00Z"}')
packed=$(jq -nc --argjson id "$ORDER_ID" '{eventId:"8327a2ac-921c-4cb7-bc90-38dfc904c47c",orderId:$id,revision:1,status:"PACKED",occurredAt:"2026-10-27T13:12:00Z"}')
delayed=$(jq -nc --argjson id "$ORDER_ID" '{eventId:"0d40b6f3-6a0c-4cdd-91b8-a81ab039609b",orderId:$id,revision:2,status:"DELAYED",occurredAt:"2026-10-27T13:15:00Z"}')

printf 'A: checkout only\n'
post order-placed "$checkout"
await_context '.checkout.status == "PLACED" and .complete == false and .missingSources == ["inventory","shipping"]'

printf 'B: all sources, shipping revision 2\n'
post inventory "$inventory"
post shipping "$packed"
post shipping "$delayed"
await_context '.complete == true and .shipping.status == "DELAYED" and .shipping.revision == 2'

printf 'C: older revision and duplicate\n'
post shipping "$packed"
post shipping "$delayed"
# A later record on the same one-partition shipping topic is a processing barrier.
# It also demonstrates conflicts on a separate order, leaving the main one usable.
main_order=$ORDER_ID
ORDER_ID=$((ORDER_ID + 1))
barrier=$(jq --argjson id "$ORDER_ID" '.orderId=$id | .eventId="dc37cbdb-4522-4eb5-9385-d9621f4b0ac4"' <<< "$delayed")
post shipping "$barrier"
post shipping "$(jq '.status="PACKED" | .eventId="4fb54a25-fd10-4003-81bd-b48b888ce781"' <<< "$barrier")"
await_context '.conflict == true and .complete == false and .shipping.status == "DELAYED"'
ORDER_ID=$main_order
await_context '.complete == true and .conflict == false and .shipping.status == "DELAYED" and .shipping.revision == 2'
printf 'PASS: incomplete, complete, old, duplicate, and conflicting events\n'
