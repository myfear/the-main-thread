#!/usr/bin/env bash

set -euo pipefail

namespace="${1:-default}"
work_dir="$(mktemp -d)"
first_forward_pid=""
second_forward_pid=""

cleanup() {
    if [[ -n "${first_forward_pid}" ]]; then
        kill "${first_forward_pid}" 2>/dev/null || true
        wait "${first_forward_pid}" 2>/dev/null || true
    fi
    if [[ -n "${second_forward_pid}" ]]; then
        kill "${second_forward_pid}" 2>/dev/null || true
        wait "${second_forward_pid}" 2>/dev/null || true
    fi
    rm -rf "${work_dir}"
}
trap cleanup EXIT

pod_list="$(
    kubectl get pods \
        --namespace "${namespace}" \
        --selector app=tidalmesh \
        --field-selector status.phase=Running \
        --output jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'
)"
pod_count="$(printf '%s\n' "${pod_list}" | sed '/^$/d' | wc -l | tr -d ' ')"

if [[ "${pod_count}" -ne 2 ]]; then
    echo "Expected two running TidalMesh pods, found ${pod_count}." >&2
    exit 1
fi

first_pod="$(printf '%s\n' "${pod_list}" | sed -n '1p')"
second_pod="$(printf '%s\n' "${pod_list}" | sed -n '2p')"

kubectl port-forward --namespace "${namespace}" "pod/${first_pod}" \
    18080:8080 19990:9990 >"${work_dir}/pod-0.log" 2>&1 &
first_forward_pid=$!
kubectl port-forward --namespace "${namespace}" "pod/${second_pod}" \
    18081:8080 19991:9990 >"${work_dir}/pod-1.log" 2>&1 &
second_forward_pid=$!

for port in 19990 19991; do
    for attempt in {1..30}; do
        if curl --silent --fail "http://127.0.0.1:${port}/health/ready" >/dev/null; then
            break
        fi
        if [[ "${attempt}" -eq 30 ]]; then
            echo "Port-forward on ${port} did not become ready." >&2
            exit 1
        fi
        sleep 1
    done
done

first_response="$(
    curl --silent --show-error --fail \
        --cookie-jar "${work_dir}/cookies.txt" \
        --request POST \
        http://127.0.0.1:18080/api/orders/ORD-42/check-ins
)"

second_response="$(
    curl --silent --show-error --fail \
        --cookie "${work_dir}/cookies.txt" \
        --request POST \
        http://127.0.0.1:18081/api/orders/ORD-42/check-ins
)"

first_count="$(jq --raw-output '.checkIns' <<<"${first_response}")"
second_count="$(jq --raw-output '.checkIns' <<<"${second_response}")"
first_session="$(jq --raw-output '.sessionId' <<<"${first_response}")"
second_session="$(jq --raw-output '.sessionId' <<<"${second_response}")"
first_node="$(jq --raw-output '.nodeName' <<<"${first_response}")"
second_node="$(jq --raw-output '.nodeName' <<<"${second_response}")"

if [[ "${first_count}" != "1" || "${second_count}" != "2" ]]; then
    echo "Expected replicated counts 1 and 2." >&2
    echo "${first_response}" >&2
    echo "${second_response}" >&2
    exit 1
fi

if [[ "${first_session}" != "${second_session}" ]]; then
    echo "The HTTP session ID changed between pods." >&2
    exit 1
fi

if [[ "${first_node}" == "${second_node}" ]]; then
    echo "Both responses came from ${first_node}; expected two nodes." >&2
    exit 1
fi

jq --null-input \
    --arg firstNode "${first_node}" \
    --arg secondNode "${second_node}" \
    --arg sessionId "${first_session}" \
    '{
        firstNode: $firstNode,
        secondNode: $secondNode,
        sessionId: $sessionId,
        replicatedCounts: [1, 2]
    }'
