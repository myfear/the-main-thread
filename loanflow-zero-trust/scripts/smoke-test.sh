#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CA="${ROOT}/infrastructure/certs/ca/ca.crt"
LOAN_CERT="${ROOT}/infrastructure/certs/loan-service"
KEYCLOAK="http://localhost:8180/realms/loanflow/protocol/openid-connect/token"

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1"; exit 1; }
}

require curl
require jq

if [[ ! -f "${CA}" ]]; then
  echo "Run scripts/generate-certs.sh first"
  exit 1
fi

if ! curl -sf "http://localhost:8180/realms/loanflow" >/dev/null; then
  echo "Keycloak is not reachable on port 8180."
  echo "Start at least one service with ./mvnw quarkus:dev (loan-service first is fine)."
  exit 1
fi

echo "==> Fetch Alice user token"
USER_TOKEN=$(
  curl -sf "${KEYCLOAK}" \
    --user loanflow-cli:loanflow-cli-secret \
    -H 'content-type: application/x-www-form-urlencoded' \
    -d 'username=alice&password=alice&grant_type=password' | jq -r '.access_token'
)
test -n "${USER_TOKEN}" && test "${USER_TOKEN}" != "null" || { echo "Failed to fetch Alice token"; exit 1; }

echo "==> Alice reads Berlin loan (expect 200)"
curl -sf --cacert "${CA}" \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "https://localhost:8443/api/loans/LN-100" >/dev/null
echo "OK"

echo "==> Fetch Bob user token"
BOB_TOKEN=$(
  curl -sf "${KEYCLOAK}" \
    --user loanflow-cli:loanflow-cli-secret \
    -H 'content-type: application/x-www-form-urlencoded' \
    -d 'username=bob&password=bob&grant_type=password' | jq -r '.access_token'
)
test -n "${BOB_TOKEN}" && test "${BOB_TOKEN}" != "null" || { echo "Failed to fetch Bob token"; exit 1; }

echo "==> Bob reads Berlin loan (expect 403)"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' --cacert "${CA}" \
  -H "Authorization: Bearer ${BOB_TOKEN}" \
  "https://localhost:8443/api/loans/LN-100")
[[ "${STATUS}" == "403" ]] || { echo "Expected 403, got ${STATUS}"; exit 1; }
echo "OK"

echo "==> Fetch loan-service client token"
SERVICE_TOKEN=$(
  curl -sf "${KEYCLOAK}" \
    --user loan-service:loan-service-secret \
    -H 'content-type: application/x-www-form-urlencoded' \
    -d 'grant_type=client_credentials&scope=credit_check_run document_write' | jq -r '.access_token'
)
test -n "${SERVICE_TOKEN}" && test "${SERVICE_TOKEN}" != "null" || { echo "Failed to fetch service token"; exit 1; }

echo "==> Direct credit-service without mTLS (expect TLS failure)"
set +e
curl -s --cacert "${CA}" \
  -H "Authorization: Bearer ${SERVICE_TOKEN}" \
  -H 'content-type: application/json' \
  -d '{"loanId":"LN-100","applicantId":"alice"}' \
  "https://localhost:8444/internal/credit-checks" >/dev/null 2>&1
TLS_RC=$?
set -e
[[ "${TLS_RC}" -ne 0 ]] || { echo "Expected TLS handshake failure without client cert"; exit 1; }
echo "OK (TLS rejected)"

echo "==> Direct credit-service with mTLS but no bearer token (expect 403)"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' --cacert "${CA}" \
  --cert "${LOAN_CERT}/tls.crt" \
  --key "${LOAN_CERT}/tls.key" \
  -H 'content-type: application/json' \
  -d '{"loanId":"LN-100","applicantId":"alice"}' \
  "https://localhost:8444/internal/credit-checks")
[[ "${STATUS}" == "403" ]] || { echo "Expected 403, got ${STATUS}"; exit 1; }
echo "OK"

echo "==> Direct credit-service with mTLS and service token (expect 200)"
curl -sf --cacert "${CA}" \
  --cert "${LOAN_CERT}/tls.crt" \
  --key "${LOAN_CERT}/tls.key" \
  -H "Authorization: Bearer ${SERVICE_TOKEN}" \
  -H 'content-type: application/json' \
  -d '{"loanId":"LN-100","applicantId":"alice"}' \
  "https://localhost:8444/internal/credit-checks" >/dev/null
echo "OK"

echo "==> Submit loan through edge API (expect 200, or 409 if already submitted)"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' --cacert "${CA}" \
  -X POST \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "https://localhost:8443/api/loans/LN-100/submit")
if [[ "${STATUS}" == "200" ]]; then
  echo "OK"
elif [[ "${STATUS}" == "409" ]]; then
  echo "OK (already submitted — restart loan-service for a fresh DRAFT seed)"
else
  echo "Expected 200 or 409, got ${STATUS}"
  exit 1
fi

echo "==> Resubmit same loan (expect 409)"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' --cacert "${CA}" \
  -X POST \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "https://localhost:8443/api/loans/LN-100/submit")
[[ "${STATUS}" == "409" ]] || { echo "Expected 409, got ${STATUS}"; exit 1; }
echo "OK"

echo "All smoke checks passed."
