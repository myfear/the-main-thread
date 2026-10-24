#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
curl --max-time 5 -fsS "$CONNECT_URL/connectors/checkout-outbox-cdc/status" | jq .
sql -P pager=off < config/debezium/slot-status.sql
