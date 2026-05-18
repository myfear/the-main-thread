#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROFILE="${1:-}"
ACTIVE_PROFILE="benchmark"
LABEL="baseline"

if [[ -n "$PROFILE" ]]; then
  ACTIVE_PROFILE="benchmark,${PROFILE}"
  LABEL="$PROFILE"
fi

PORT=18080
BASE_URL="http://127.0.0.1:${PORT}"
LOG_FILE="$(mktemp)"
RUNNER_JAR="target/quarkus-app/quarkus-run.jar"

echo "== CatalogAPI JSON comparison ($LABEL) =="

echo "-- Packaging JVM runner --"
./mvnw -q package -DskipTests "-Dquarkus.profile=$ACTIVE_PROFILE"

echo "-- Measuring cold JVM startup --"
START_MS="$(python3 -c 'import time; print(int(time.time() * 1000))')"

java -Dquarkus.http.port="$PORT" -Dquarkus.profile="$ACTIVE_PROFILE" -jar "$RUNNER_JAR" >"$LOG_FILE" 2>&1 &
APP_PID=$!

READY=0
for _ in $(seq 1 120); do
  if curl -sf "$BASE_URL/products" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 0.25
done

END_MS="$(python3 -c 'import time; print(int(time.time() * 1000))')"

if [[ "$READY" -ne 1 ]]; then
  echo "Application did not become ready on $BASE_URL" >&2
  tail -n 40 "$LOG_FILE" >&2 || true
  kill "$APP_PID" >/dev/null 2>&1 || true
  exit 1
fi

STARTUP_MS="$((END_MS - START_MS))"
echo "Cold startup to first successful GET /products: ${STARTUP_MS} ms"

echo "-- Measuring simple throughput (GET /products/summaries) --"
if command -v hey >/dev/null 2>&1; then
  hey -z 5s -c 8 "$BASE_URL/products/summaries" | awk '/Requests\/sec/ {print "Throughput:", $2, "req/s"}'
else
  COUNT=0
  END_LOOP="$(python3 -c 'import time; print(time.time() + 5)')"
  while python3 -c "import time; raise SystemExit(0 if time.time() < $END_LOOP else 1)"; do
    curl -sf "$BASE_URL/products/summaries" >/dev/null
    COUNT=$((COUNT + 1))
  done
  RPS="$(python3 -c "print(round($COUNT / 5, 2))")"
  echo "Throughput (curl loop, 5s): ${RPS} req/s (install hey for a better sample)"
fi

NATIVE_RUNNER=(target/*-runner)
if [[ -f "${NATIVE_RUNNER[0]:-}" ]]; then
  echo "Native runner size: $(du -h "${NATIVE_RUNNER[0]}" | awk '{print $1}')"
fi

kill "$APP_PID" >/dev/null 2>&1 || true
wait "$APP_PID" 2>/dev/null || true

if grep -q "started in" "$LOG_FILE"; then
  grep "started in" "$LOG_FILE" | tail -n 1
fi

echo "Done ($LABEL). Log: $LOG_FILE"
echo ""
echo "Compare profiles:"
echo "  ./scripts/compare-json-serialization.sh"
echo "  ./scripts/compare-json-serialization.sh reflection-free"
echo ""
echo "Native build (optional, container runtime required):"
echo "  ./mvnw package -Dnative -Dquarkus.native.container-build=true -Dquarkus.profile=benchmark"
