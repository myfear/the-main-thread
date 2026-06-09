#!/usr/bin/env bash
# Smoke checks for dockyard-discovery. Requires:
# - Consul on :8500 (see README)
# - catalog-service on :8081 and :8082 (two terminals)
# - checkout-service on :8080
set -euo pipefail

echo "==> Consul leader"
curl -sf http://localhost:8500/v1/status/leader | grep -q '127.0.0.1'

echo "==> two passing catalog-service instances"
ports=$(curl -sf 'http://localhost:8500/v1/health/service/catalog-service?passing=true' \
  | python3 -c "import sys,json; print(' '.join(str(s['Service']['Port']) for s in json.load(sys.stdin)))")
echo "$ports" | grep -q '8081'
echo "$ports" | grep -q '8082'

echo "==> round-robin via checkout"
seen=""
for i in 1 2 3 4 5 6; do
  id=$(curl -sf http://localhost:8080/quote/sku-1 | python3 -c "import sys,json; print(json.load(sys.stdin)['instanceId'])")
  seen="$seen $id"
done
echo "$seen" | grep -q 'catalog-1'
echo "$seen" | grep -q 'catalog-2'

echo "All smoke checks passed."
