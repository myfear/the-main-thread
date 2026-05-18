#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODE="${1:-}"
if [[ "$MODE" != "naive" && "$MODE" != "graceful" ]]; then
  echo "Usage: $0 <naive|graceful>" >&2
  exit 1
fi

PORT=18080
BASE_URL="http://127.0.0.1:${PORT}"
LOG_FILE="$(mktemp)"
HANDOFF_LOG="$(mktemp)"
READINESS_LOG="$(mktemp)"
RUNNER_JAR="target/quarkus-app/quarkus-run.jar"

if [[ "$MODE" == "graceful" ]]; then
  ACTIVE_PROFILE="graceful,script"
else
  ACTIVE_PROFILE="script"
fi

cleanup() {
  if [[ -n "${APP_PID:-}" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  rm -f "$LOG_FILE" "$HANDOFF_LOG" "$READINESS_LOG"
}
trap cleanup EXIT

echo "== OrderBridge shutdown demo ($MODE) =="
echo "-- Packaging JVM runner (profile: $ACTIVE_PROFILE) --"
./mvnw -q package -DskipTests "-Dquarkus.profile=$ACTIVE_PROFILE"

echo "-- Starting application on port $PORT --"
java -Dquarkus.http.port="$PORT" -Dquarkus.profile="$ACTIVE_PROFILE" -jar "$RUNNER_JAR" >"$LOG_FILE" 2>&1 &
APP_PID=$!

READY=0
for _ in $(seq 1 120); do
  if curl -sf "$BASE_URL/q/health/ready" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 0.25
done

if [[ "$READY" -ne 1 ]]; then
  echo "Application did not become ready on $BASE_URL" >&2
  tail -n 40 "$LOG_FILE" >&2 || true
  exit 1
fi

echo "-- Readiness before shutdown: UP --"
curl -s -o /dev/null -w "ready HTTP %{http_code}\n" "$BASE_URL/q/health/ready"

echo "-- Starting in-flight payment handoff --"
curl -s -X POST "$BASE_URL/orders/handoff" \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORD-SHUTDOWN","amountCents":9900}' \
  --max-time 30 \
  -w "\nhandoff HTTP %{http_code} (total %{time_total}s)\n" \
  >"$HANDOFF_LOG" 2>&1 &
HANDOFF_PID=$!

sleep 0.5

echo "-- Sending SIGTERM to PID $APP_PID --"
kill -TERM "$APP_PID"

echo "-- Polling readiness during shutdown --"
READINESS_WENT_DOWN=0
for _ in $(seq 1 40); do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/q/health/ready" || echo "000")"
  TS="$(date +%H:%M:%S)"
  echo "[$TS] readiness HTTP $CODE" | tee -a "$READINESS_LOG"
  if [[ "$CODE" != "200" ]]; then
    READINESS_WENT_DOWN=1
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    break
  fi
  sleep 0.2
done

wait "$HANDOFF_PID" 2>/dev/null || true

echo ""
echo "-- Handoff result --"
cat "$HANDOFF_LOG"
echo ""

echo "-- Recent application logs --"
tail -n 25 "$LOG_FILE" || true
echo ""

FAIL=0
if [[ "$MODE" == "graceful" ]]; then
  if ! grep -q "handoff HTTP 200" "$HANDOFF_LOG"; then
    echo "Expected in-flight handoff to complete with HTTP 200 in graceful mode." >&2
    FAIL=1
  fi
  if [[ "$READINESS_WENT_DOWN" -ne 1 ]]; then
    echo "Expected readiness to fail during graceful shutdown delay." >&2
    FAIL=1
  fi
else
  echo "Naive mode note: without shutdown timeout/delay, the client often loses the connection (HTTP 000) even if the server log shows the handoff finishing."
fi

if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi

echo "Shutdown demo ($MODE) finished."
