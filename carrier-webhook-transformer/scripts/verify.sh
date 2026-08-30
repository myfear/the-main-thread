#!/usr/bin/env bash
set -euo pipefail

payload='{"event_id":"pb-2001","parcel":{"tracking":"PB200100"},"event":"parcel.in_transit","occurred_at":"2026-08-29T09:30:00Z"}'
secret="${CARRIER_WEBHOOK_SECRET:-local-demo-secret-change-before-deploy}"
signature="sha256=$(printf %s "$payload" | openssl dgst -sha256 -hmac "$secret" -hex | sed 's/^.* //')"

response="$(curl -sS -X POST http://localhost:8080/webhooks/parcelbird \
  -H 'Content-Type: application/json' \
  -H "X-Carrier-Signature: $signature" \
  --data "$payload")"

printf '%s\n' "$response" | jq -e '
  .result == "accepted" and
  .transformerVersion == "parcelbird-2026-08-29.1" and
  .shipment.trackingNumber == "PB200100" and
  .shipment.status == "IN_TRANSIT"
' >/dev/null

printf '%s\n' "$response" | jq .
