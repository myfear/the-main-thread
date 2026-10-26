#!/usr/bin/env bash
set -euo pipefail

base="${STUDIO_BASE_URL:-http://127.0.0.1:8126}"
token="${STUDIO_RUNNER_TOKEN:-local-spike-runner}"
revision=$(( $(date +%s) % 999999998 + 1 ))
version="0.0.$revision"
next_version="0.0.$((revision + 1))"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
for command in curl jq cmp; do
    command -v "$command" >/dev/null || { echo "Required command: $command" >&2; exit 1; }
done

request() {
    curl --silent --show-error --fail-with-body --max-time 15 \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' "$@"
}

request "$base/api/graph-examples/pr-review" > "$scratch/graph.json"
request -X POST "$base/api/graphs/validate" --data-binary "@$scratch/graph.json" > "$scratch/validation.json"
jq -e '.valid and (.errors | length == 0)' "$scratch/validation.json" >/dev/null

jq -nc --arg version "$version" --slurpfile graph "$scratch/graph.json" \
    '{version: $version, graph: $graph[0]}' > "$scratch/publish-request.json"
request -X POST "$base/api/graphs/compile" --data-binary "@$scratch/publish-request.json" > "$scratch/compile-first.json"
request -X POST "$base/api/graphs/compile" --data-binary "@$scratch/publish-request.json" > "$scratch/compile-second.json"
cmp <(jq -S . "$scratch/compile-first.json") <(jq -S . "$scratch/compile-second.json")
jq -e --arg token "$token" \
    '(.manifest | length == 7) and
     (.manifest | has("n_model") | not) and
     (.manifest | has("n_prompt") | not) and
     (.manifest | has("n_tools") | not) and
     (.manifest.n_review.agent.modelRef == "fixture-model-v1") and
     (.manifest.n_review.agent.allowedToolIds == ["pr.read-files"]) and
     (.yaml | contains($token) | not)' "$scratch/compile-first.json" >/dev/null

publish() {
    local input="$1" receipt="$2" ready=false
    request -X POST "$base/api/graphs/publish" --data-binary "@$input" > "$receipt"
    publication="$(jq -er '.publicationId' "$receipt")"
    for (( attempt=0; attempt<100; attempt++ )); do
        request "$base/api/publications/$publication" > "$scratch/publication-status.json"
        if jq -e '.status == "READY"' "$scratch/publication-status.json" >/dev/null; then ready=true; break; fi
        if jq -e '.status == "FAILED_TO_DEPLOY"' "$scratch/publication-status.json" >/dev/null; then
            cat "$scratch/publication-status.json" >&2
            exit 1
        fi
        sleep 0.1
    done
    [[ "$ready" == true ]] || { echo "Compiled workflow discovery timed out." >&2; exit 1; }
}

run_case() {
    local name="$1" publication_id="$2" fixture="$3" decision="$4" selected="$5" unselected="$6"
    local finished=false
    request -X POST "$base/api/publications/$publication_id/runs" \
        -d "$(jq -nc --arg fixture "$fixture" '{fixture: $fixture}')" > "$scratch/$name-start.json"
    local run_id
    run_id="$(jq -er '.runId' "$scratch/$name-start.json")"
    for (( attempt=0; attempt<100; attempt++ )); do
        request "$base/api/graph-runs/$run_id" > "$scratch/$name.json"
        if jq -e '.status == "COMPLETED"' "$scratch/$name.json" >/dev/null; then finished=true; break; fi
        if jq -e '.status == "FAILED" or .status == "START_UNCERTAIN"' "$scratch/$name.json" >/dev/null; then
            cat "$scratch/$name.json" >&2
            exit 1
        fi
        sleep 0.1
    done
    [[ "$finished" == true ]] || { echo "Graph run timed out." >&2; exit 1; }
    jq -e --arg runId "$run_id" --arg publication "$publication_id" --arg decision "$decision" \
        --arg selected "$selected" --arg unselected "$unselected" \
        '.publicationId == $publication and .lostEvents == 0 and .output.decision == $decision and
         .output.modelMode == "fixture" and
         (any(.events[]; .kind == "TASK_STARTED" and .nodeId == $selected)) and
         (all(.events[]; .nodeId != $unselected)) and
         (all(.events[]; .runId == $runId)) and
         (all(.events[] | select(.kind | startswith("TASK_")); .nodeId != null))' \
        "$scratch/$name.json" >/dev/null
    jq -nc --arg case "$name" --arg runId "$run_id" --arg decision "$decision" \
        '{case: $case, runId: $runId, decision: $decision}'
}

publish "$scratch/publish-request.json" "$scratch/publication-v1.json"
first="$publication"
request "$base/api/publications/$first/compiled" > "$scratch/published-v1.json"
cmp <(jq -S . "$scratch/compile-first.json") <(jq -S . "$scratch/published-v1.json")
run_case low-risk "$first" low-risk LOW_RISK lowOutput highOutput
run_case high-risk "$first" high-risk REQUIRES_HUMAN_REVIEW highOutput lowOutput

jq --arg version "$next_version" \
    '.version = $version | (.graph.nodes[] | select(.id == "policy") | .config.minimumSeverity) = "LOW"' \
    "$scratch/publish-request.json" > "$scratch/publish-v2-request.json"
publish "$scratch/publish-v2-request.json" "$scratch/publication-v2.json"
second="$publication"
run_case changed-policy "$second" low-risk REQUIRES_HUMAN_REVIEW highOutput lowOutput
run_case original-version "$first" low-risk LOW_RISK lowOutput highOutput
request "$base/api/publications/$first/compiled" > "$scratch/retained-v1.json"
cmp <(jq -S . "$scratch/published-v1.json") <(jq -S . "$scratch/retained-v1.json")

jq '(.graph.nodes[] | select(.id == "fetch") | .config.url) = "https://attacker.invalid"' \
    "$scratch/publish-request.json" > "$scratch/invalid-request.json"
code="$(curl --silent --show-error --max-time 15 -o "$scratch/invalid-response.json" -w '%{http_code}' \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data-binary "@$scratch/invalid-request.json" "$base/api/graphs/publish")"
[[ "$code" == 422 ]] || { echo "Invalid graph returned $code." >&2; exit 1; }
jq -e '.valid == false and any(.errors[]; .nodeId == "fetch" and .code == "INVALID_CONFIG")' \
    "$scratch/invalid-response.json" >/dev/null

if [[ -n "${STUDIO_GRAPH_SMOKE_OUTPUT:-}" ]]; then
    mkdir -p "$STUDIO_GRAPH_SMOKE_OUTPUT"
    cp "$scratch/"*.json "$STUDIO_GRAPH_SMOKE_OUTPUT/"
fi
echo "Graph contract passed for $version and $next_version."
