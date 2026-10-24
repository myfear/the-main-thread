#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
umask 077
mkdir -p target
rendered=$(mktemp target/connector.XXXXXX)
trap 'rm -f "$rendered"' EXIT
jq '.config | ."database.password" = env.CDC_PASSWORD' config/debezium/checkout-outbox-connector.json > "$rendered"
validation=$(curl --max-time 30 -fsS -X PUT -H 'Content-Type: application/json' --data-binary "@$rendered" "$CONNECT_URL/connector-plugins/io.debezium.connector.postgresql.PostgresConnector/config/validate" | jq '{error_count, errors: [.configs[] | select(.value.errors | length > 0) | {name: .definition.name, errors: .value.errors}]}')
echo "$validation"
[[ $(jq -r .error_count <<< "$validation") == 0 ]]
curl --max-time 30 -fsS -X PUT -H 'Content-Type: application/json' --data-binary "@$rendered" "$CONNECT_URL/connectors/checkout-outbox-cdc/config" | jq '{name, tasks}'
ready() {
  curl --max-time 3 -fsS "$CONNECT_URL/connectors/checkout-outbox-cdc/status" |
    jq -e '.connector.state == "RUNNING" and (.tasks | length) == 1 and all(.tasks[]; .state == "RUNNING")' >/dev/null
}
wait_for 'connector and task' ready
wait_for 'active logical slot' bash -c 'podman compose exec -T postgres psql -X -Atqc "SELECT active FROM pg_replication_slots WHERE slot_name = '\''checkout_outbox_slot'\''" -U checkout -d checkout | grep -qx t'
curl --max-time 3 -fsS "$CONNECT_URL/connectors/checkout-outbox-cdc/status" | jq .
