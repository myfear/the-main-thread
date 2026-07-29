#!/usr/bin/env bash

set -Eeuo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
RUNTIME_DIR="$ROOT/.runtime"
TOKEN_ENDPOINT="http://localhost:8180/realms/delegation/protocol/openid-connect/token"
PIDS=()

fail() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

pass() {
    printf 'PASS: %s\n' "$1"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

wait_for_url() {
    local url=$1
    local attempts=${2:-60}

    for ((attempt = 1; attempt <= attempts; attempt++)); do
        if curl -fsS "$url" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    fail "timed out waiting for $url"
}

wait_for_service() {
    local url=$1
    local attempts=${2:-60}

    for ((attempt = 1; attempt <= attempts; attempt++)); do
        local status
        status=$(curl -sS --connect-timeout 1 -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
        if [[ "$status" != "000" && -n "$status" ]]; then
            return 0
        fi
        sleep 1
    done

    fail "timed out waiting for $url"
}

cleanup() {
    local exit_code=$?

    podman compose -f "$ROOT/compose.yaml" start keycloak >/dev/null 2>&1 || true
    for pid in "${PIDS[@]:-}"; do
        kill "$pid" >/dev/null 2>&1 || true
    done
    wait >/dev/null 2>&1 || true

    if ((exit_code != 0)); then
        printf 'Service logs are in %s\n' "$RUNTIME_DIR" >&2
    fi
}

get_user_token() {
    curl -fsS -X POST "$TOKEN_ENDPOINT" \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -d grant_type=password \
        -d client_id=tutorial-client \
        -d username=alice \
        -d password=alice | jq -er .access_token
}

get_pkce_token() {
    local verifier challenge action location code
    verifier=$(openssl rand -hex 32)
    challenge=$(printf '%s' "$verifier" \
        | openssl dgst -sha256 -binary \
        | openssl base64 -A \
        | tr '+/' '-_' \
        | tr -d '=')

    curl -fsS -c "$RUNTIME_DIR/pkce.cookies" \
        "http://localhost:8180/realms/delegation/protocol/openid-connect/auth?client_id=tutorial-client&response_type=code&scope=openid&redirect_uri=http%3A%2F%2F127.0.0.1%3A3000%2Fcallback&code_challenge=$challenge&code_challenge_method=S256&state=verification" \
        -o "$RUNTIME_DIR/pkce-login.html"

    action=$(sed -n 's/.*<form[^>]*id="kc-form-login"[^>]*action="\([^"]*\)".*/\1/p' \
        "$RUNTIME_DIR/pkce-login.html" | head -1 | sed 's/&amp;/\&/g')
    [[ -n "$action" ]] || fail 'Keycloak login form did not contain an action'

    curl -sS -b "$RUNTIME_DIR/pkce.cookies" -c "$RUNTIME_DIR/pkce.cookies" \
        -D "$RUNTIME_DIR/pkce.headers" \
        -o "$RUNTIME_DIR/pkce-login-result.html" \
        -X POST "$action" \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -d username=alice \
        -d password=alice \
        -d credentialId=

    location=$(sed -n 's/^[Ll]ocation: //p' "$RUNTIME_DIR/pkce.headers" | tr -d '\r' | tail -1)
    [[ "$location" == http://127.0.0.1:3000/callback* ]] \
        || fail 'authorization code flow did not return to the registered redirect URI'
    code=$(printf '%s' "$location" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
    [[ -n "$code" ]] || fail 'authorization response did not include a code'

    curl -fsS -X POST "$TOKEN_ENDPOINT" \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -d grant_type=authorization_code \
        -d client_id=tutorial-client \
        -d redirect_uri=http://127.0.0.1:3000/callback \
        -d code="$code" \
        -d code_verifier="$verifier" | jq -er .access_token
}

exchange_token() {
    local client_id=$1
    local client_secret=$2
    local subject_token=$3
    local audience=$4

    curl -fsS -X POST "$TOKEN_ENDPOINT" \
        -u "$client_id:$client_secret" \
        -d grant_type=urn:ietf:params:oauth:grant-type:token-exchange \
        -d subject_token="$subject_token" \
        -d subject_token_type=urn:ietf:params:oauth:token-type:access_token \
        -d requested_token_type=urn:ietf:params:oauth:token-type:access_token \
        -d audience="$audience" | jq -er .access_token
}

for command in curl java jq openssl podman; do
    require_command "$command"
done

trap cleanup EXIT INT TERM

mkdir -p "$RUNTIME_DIR"

podman compose -f "$ROOT/compose.yaml" up -d keycloak >/dev/null
wait_for_url http://localhost:9000/health/ready
pass 'Keycloak is ready'

ADMIN_TOKEN=$(curl -fsS -X POST http://localhost:8180/realms/master/protocol/openid-connect/token \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d grant_type=password \
    -d client_id=admin-cli \
    -d username=admin \
    -d password=admin | jq -er .access_token)

TUTORIAL_CLIENT=$(curl -fsS \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    'http://localhost:8180/admin/realms/delegation/clients?clientId=tutorial-client')

jq -e 'length == 1
    and .[0].standardFlowEnabled
    and .[0].directAccessGrantsEnabled
    and .[0].attributes["pkce.code.challenge.method"] == "S256"' \
    <<<"$TUTORIAL_CLIENT" >/dev/null || fail 'browser client or PKCE configuration is wrong'
pass 'browser client uses authorization code flow with S256 PKCE'

CLIENT_PROFILES=$(curl -fsS \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    http://localhost:8180/admin/realms/delegation/client-policies/profiles)
CLIENT_POLICIES=$(curl -fsS \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    http://localhost:8180/admin/realms/delegation/client-policies/policies)

jq -e '.profiles[]
    | select(.name == "token-exchange-downscope")
    | .executors[]
    | select(.executor == "downscope-assertion-grant-enforcer")' \
    <<<"$CLIENT_PROFILES" >/dev/null || fail 'downscope client profile is missing'
jq -e '.policies[]
    | select(.name == "token-exchange-only-downscope")
    | .enabled == true' \
    <<<"$CLIENT_POLICIES" >/dev/null || fail 'downscope client policy is missing or disabled'
pass 'Keycloak downscope policy is active'

build_pids=()
for service in order-service inventory-service audit-service; do
    (
        cd "$ROOT/$service"
        ./mvnw -q package
    ) >"$RUNTIME_DIR/$service-build.log" 2>&1 &
    build_pids+=("$!")
done

for pid in "${build_pids[@]}"; do
    wait "$pid" || fail 'a service build failed'
done
pass 'all three Quarkus services build'

java -jar "$ROOT/audit-service/target/quarkus-app/quarkus-run.jar" \
    >"$RUNTIME_DIR/audit-service.log" 2>&1 &
PIDS+=("$!")

SERVICE_B_SECRET=service-b-secret \
    java -jar "$ROOT/inventory-service/target/quarkus-app/quarkus-run.jar" \
    >"$RUNTIME_DIR/inventory-service.log" 2>&1 &
PIDS+=("$!")

SERVICE_A_SECRET=service-a-secret \
    java -jar "$ROOT/order-service/target/quarkus-app/quarkus-run.jar" \
    >"$RUNTIME_DIR/order-service.log" 2>&1 &
PIDS+=("$!")

wait_for_service http://localhost:8081/orders/readiness/submit
wait_for_service http://localhost:8082/reservations
wait_for_service http://localhost:8083/audit-events
pass 'all three services are listening'

INITIAL_TOKEN=$(get_pkce_token)
pass 'authorization code flow with S256 PKCE issues the initial token'
SUCCESS_RESPONSE=$(curl -fsS -X POST http://localhost:8081/orders/order-42/submit \
    -H "Authorization: Bearer $INITIAL_TOKEN" \
    -H 'X-Correlation-ID: verification-run')

jq -e '
    .orderId == "order-42"
    and .status == "submitted"
    and (.hops | length == 3)
    and .hops[0].audience == ["service-a"]
    and .hops[1].audience == ["service-b"]
    and .hops[2].audience == ["service-c"]
    and .hops[0].authorizedParty == "tutorial-client"
    and .hops[1].authorizedParty == "service-a"
    and .hops[2].authorizedParty == "service-b"
    and ([.hops[].subject] | unique | length == 1)
    and ([.hops[].username] | unique) == ["alice"]
    and ([.hops[].correlationId] | unique) == ["verification-run"]
    and ([.hops[].tokenId] | unique | length == 3)
' <<<"$SUCCESS_RESPONSE" >/dev/null || fail 'the A to B to C claim trace is wrong'
pass 'A to B to C exchanges preserve identity and narrow each audience'

B_STATUS=$(curl -sS -o "$RUNTIME_DIR/wrong-audience-b.json" -w '%{http_code}' \
    -X POST http://localhost:8082/reservations \
    -H "Authorization: Bearer $INITIAL_TOKEN" \
    -H 'Content-Type: application/json' \
    -H 'X-Correlation-ID: wrong-audience-b' \
    -d '{"orderId":"wrong-audience","quantity":1}')
C_STATUS=$(curl -sS -o "$RUNTIME_DIR/wrong-audience-c.json" -w '%{http_code}' \
    -X POST http://localhost:8083/audit-events \
    -H "Authorization: Bearer $INITIAL_TOKEN" \
    -H 'Content-Type: application/json' \
    -H 'X-Correlation-ID: wrong-audience-c' \
    -d '{"orderId":"wrong-audience","action":"rejected"}')

[[ "$B_STATUS" == "401" && "$C_STATUS" == "401" ]] \
    || fail "wrong-audience status was B=$B_STATUS C=$C_STATUS"
pass 'services B and C reject the original service-a token'

REQUESTER_STATUS=$(curl -sS -o "$RUNTIME_DIR/requester-audience.json" -w '%{http_code}' \
    -X POST "$TOKEN_ENDPOINT" \
    -u service-b:service-b-secret \
    -d grant_type=urn:ietf:params:oauth:grant-type:token-exchange \
    -d subject_token="$INITIAL_TOKEN" \
    -d subject_token_type=urn:ietf:params:oauth:token-type:access_token \
    -d audience=service-c)

[[ "$REQUESTER_STATUS" == "403" ]] || fail "unexpected requester-audience status: $REQUESTER_STATUS"
jq -e '.error == "access_denied"
    and (.error_description | contains("not within the token audience"))' \
    "$RUNTIME_DIR/requester-audience.json" >/dev/null \
    || fail 'Keycloak did not enforce the requester-audience rule'
pass 'service-b cannot exchange a token that was issued only to service-a'

SCOPE_STATUS=$(curl -sS -o "$RUNTIME_DIR/scope-expansion.json" -w '%{http_code}' \
    -X POST "$TOKEN_ENDPOINT" \
    -u service-a:service-a-secret \
    -d grant_type=urn:ietf:params:oauth:grant-type:token-exchange \
    -d subject_token="$INITIAL_TOKEN" \
    -d subject_token_type=urn:ietf:params:oauth:token-type:access_token \
    -d audience=service-b \
    -d scope=forbidden)

[[ "$SCOPE_STATUS" == "400" ]] || fail "unexpected scope-expansion status: $SCOPE_STATUS"
jq -e '.error == "invalid_scope"
    and (.error_description | contains("not present in the initial access token"))' \
    "$RUNTIME_DIR/scope-expansion.json" >/dev/null \
    || fail 'Keycloak did not reject scope expansion'
pass 'token exchange cannot add a scope absent from the subject token'

TOKEN_B=$(exchange_token service-a service-a-secret "$INITIAL_TOKEN" service-b)
TOKEN_C=$(exchange_token service-b service-b-secret "$TOKEN_B" service-c)
[[ -n "$TOKEN_B" && -n "$TOKEN_C" ]] || fail 'raw RFC 8693 exchange did not issue both tokens'
pass 'raw RFC 8693 exchanges work with the same realm configuration'

OUTAGE_TOKEN=$(get_user_token)
podman compose -f "$ROOT/compose.yaml" stop keycloak >/dev/null
OUTAGE_STATUS=$(curl -sS -o "$RUNTIME_DIR/keycloak-outage.json" -w '%{http_code}' \
    -X POST http://localhost:8081/orders/outage/submit \
    -H "Authorization: Bearer $OUTAGE_TOKEN" \
    -H 'X-Correlation-ID: keycloak-outage')
podman compose -f "$ROOT/compose.yaml" start keycloak >/dev/null
wait_for_url http://localhost:9000/health/ready

[[ "$OUTAGE_STATUS" == "502" ]] || fail "unexpected outage status: $OUTAGE_STATUS"
jq -e '.code == "downstream_unavailable"
    and .correlationId == "keycloak-outage"' \
    "$RUNTIME_DIR/keycloak-outage.json" >/dev/null \
    || fail 'outage response was not controlled and traceable'
pass 'a Keycloak exchange outage returns a controlled 502'

printf '\nAll cascaded delegation checks passed.\n'
