#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
label="${1:?Usage: start-replica.sh LABEL HTTP_PORT}"
port="${2:?Usage: start-replica.sh LABEL HTTP_PORT}"
exec java \
  -Dquarkus.profile=demo \
  -Dquarkus.http.port="$port" \
  -Dcheckout.outbox.worker-label="$label" \
  -Dquarkus.scheduler.start-mode="${SCHEDULER_START_MODE:-normal}" \
  -Dcheckout.outbox.lease-duration="${LEASE_DURATION:-30s}" \
  -jar target/quarkus-app/quarkus-run.jar
