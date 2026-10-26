#!/usr/bin/env bash
set -euo pipefail

base="${STUDIO_BASE_URL:-http://127.0.0.1:8126}"
token="${STUDIO_RUNNER_TOKEN:-local-spike-runner}"
version="${STUDIO_SPIKE_VERSION:-0.0.$(( $(date +%s) % 999999999 + 1 ))}"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

for command in curl jq uuidgen; do
    command -v "$command" >/dev/null || { echo "Required command: $command" >&2; exit 1; }
done

request() {
    curl --silent --show-error --fail-with-body --max-time 10 \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' "$@"
}

request -X POST "$base/api/spike/workflows" \
    -d "$(jq -nc --arg version "$version" '{version: $version}')" > "$scratch/publication.json"
jq -e '.status == "PUBLISHING"' "$scratch/publication.json" >/dev/null

ready=false
for (( attempt=0; attempt<100; attempt++ )); do
    code="$(curl --silent --show-error --max-time 10 -o "$scratch/ready.json" -w '%{http_code}' \
        -H "Authorization: Bearer $token" "$base/api/spike/workflows/$version")"
    if [[ "$code" == 200 ]]; then ready=true; break; fi
    if [[ "$code" != 404 ]]; then cat "$scratch/ready.json" >&2; exit 1; fi
    sleep 0.1
done
[[ "$ready" == true ]] || { echo "Workflow discovery timed out." >&2; exit 1; }

run_case() {
    local name="$1" lines="$2" fail="$3" terminal="$4" decision="$5" selected="$6" unselected="$7"
    local run_id instance observed=false finished=false
    run_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
    request -X POST "$base/q/flow/exec/studio/risk-triage/$version" \
        -d "$(jq -nc --arg runId "$run_id" --argjson changedLines "$lines" --argjson fail "$fail" \
            '{runId: $runId, changedLines: $changedLines, fail: $fail}')" > "$scratch/$name-start.json"
    instance="$(jq -er '.instanceId | select(length > 0)' "$scratch/$name-start.json")"
    for (( attempt=0; attempt<100; attempt++ )); do
        request "$base/api/spike/instances/$instance" > "$scratch/$name.json"
        if jq -e '.engineCheckpointRows > 0' "$scratch/$name.json" >/dev/null; then
            observed=true
            cp "$scratch/$name.json" "$scratch/$name-checkpoint.json"
        fi
        if jq -e --arg terminal "$terminal" 'any(.events[]; .kind == $terminal)' "$scratch/$name.json" >/dev/null; then
            finished=true
            break
        fi
        if jq -e 'any(.events[]; .kind == "WORKFLOW_FAILED")' "$scratch/$name.json" >/dev/null; then
            cat "$scratch/$name.json" >&2
            exit 1
        fi
        sleep 0.1
    done
    [[ "$finished" == true ]] || { echo "$name did not reach $terminal." >&2; exit 1; }
    jq -e --arg runId "$run_id" --arg version "$version" \
        '.lostEvents == 0 and all(.events[]; .runId == $runId and .version == $version)' \
        "$scratch/$name.json" >/dev/null
    if [[ "$terminal" == WORKFLOW_COMPLETED ]]; then
        [[ "$observed" == true ]] || { echo "$name had no observed engine checkpoints." >&2; exit 1; }
        jq -e --arg decision "$decision" --arg selected "$selected" --arg unselected "$unselected" \
            '(any(.events[]; .kind == "WORKFLOW_COMPLETED" and .output.decision == $decision)) and
             (any(.events[]; .task == $selected)) and
             (all(.events[]; .task != $unselected))' "$scratch/$name.json" >/dev/null
    else
        jq -e 'any(.events[]; .kind == "TASK_FAILED" and .task == "policy") and
            all(.events[]; .kind != "WORKFLOW_COMPLETED")' "$scratch/$name.json" >/dev/null
    fi
    jq -nc --arg case "$name" --arg instanceId "$instance" --arg terminal "$terminal" \
        --argjson observedCheckpoint "$observed" \
        '{case: $case, instanceId: $instanceId, terminal: $terminal, observedCheckpoint: $observedCheckpoint}'
}

run_case low-risk 12 false WORKFLOW_COMPLETED LOW_RISK lowRiskOutput humanReview
run_case high-risk 250 false WORKFLOW_COMPLETED REQUIRES_HUMAN_REVIEW humanReview lowRiskOutput
run_case executor-failure 12 true WORKFLOW_FAILED '' '' ''

code="$(curl --silent --show-error --max-time 10 -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' -d '{"runId":"00000000-0000-4000-8000-000000000001","changedLines":1,"fail":false}' \
    "$base/internal/spike/policy")"
[[ "$code" == 401 ]] || { echo "Unauthenticated executor returned $code." >&2; exit 1; }

if [[ -n "${STUDIO_SMOKE_OUTPUT:-}" ]]; then
    mkdir -p "$STUDIO_SMOKE_OUTPUT"
    cp "$scratch/"*.json "$STUDIO_SMOKE_OUTPUT/"
fi
echo "Runtime spike passed for $version."
