#!/usr/bin/env bash
set -euo pipefail

service_url="${SERVICE_URL:-http://localhost:8080}"
case_file="${1:-eval/cases.jsonl}"
failures=0

while IFS= read -r case_json; do
    case_id="$(jq -r '.id' <<<"${case_json}")"
    policy="$(jq -r '.policy' <<<"${case_json}")"
    direction="$(jq -r '.direction' <<<"${case_json}")"
    document="$(jq -r '.document' <<<"${case_json}")"
    expected="$(jq -r '.expected' <<<"${case_json}")"
    payload="$(jq -cn --arg document "${document}" '{document: $document}')"
    response="$(curl --fail --silent \
        --header 'Content-Type: application/json' \
        --data "${payload}" \
        "${service_url}/moderation/${policy}/${direction}")"
    actual="$(jq -r '.status' <<<"${response}")"
    score="$(jq -r '.unsafeScore // "unavailable"' <<<"${response}")"

    if [[ "${actual}" == "${expected}" ]]; then
        printf 'PASS %-24s expected=%-5s actual=%-5s score=%s\n' "${case_id}" "${expected}" "${actual}" "${score}"
    else
        printf 'FAIL %-24s expected=%-5s actual=%-5s score=%s\n' "${case_id}" "${expected}" "${actual}" "${score}"
        failures=$((failures + 1))
    fi
done < "${case_file}"

exit "${failures}"
