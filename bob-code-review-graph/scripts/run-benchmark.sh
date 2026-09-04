#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 5 ]]; then
    echo "usage: $0 OPEN_LIBERTY_CHECKOUT BOB_KEY_JSON [RUN_DIRECTORY] [all|solo|graph] [all|01-feature-resolution|02-server-startup]" >&2
    exit 2
fi

workspace_path=$(cd "$1" && pwd)
key_json=$2
script_dir=$(cd "$(dirname "$0")" && pwd)
project_dir=$(cd "$script_dir/.." && pwd)
run_directory=${3:-"$project_dir/runs/$(date -u +%Y%m%dT%H%M%SZ)"}
case_selector=${4:-all}
prompt_selector=${5:-all}

case "$case_selector" in
    all|solo|graph) ;;
    *)
        echo "invalid condition: $case_selector" >&2
        exit 2
        ;;
esac
case "$prompt_selector" in
    all|01-feature-resolution|02-server-startup) ;;
    *)
        echo "invalid prompt: $prompt_selector" >&2
        exit 2
        ;;
esac

command -v bob >/dev/null
command -v jq >/dev/null
[[ -f "$key_json" ]]
[[ -f "$workspace_path/.bob/mcp.json" ]]

bob_key=$(jq -er '.apikey | strings | select(length > 0)' "$key_json")
mkdir -p "$run_directory"

graph_navigation='Navigation condition: use code-review-graph before native repository tools. Call get_minimal_context_tool once. If the task supplies an exact qualified symbol, call query_graph_tool directly with detail_level=minimal and a small max_results, then verify selected lines with one targeted read. Otherwise call semantic_search_nodes_tool once for the starting symbol and query_graph_tool no more than twice. Answer after verification. If a graph query returns no match, use at most one exact native glob or grep rather than repeating broad searches.'
solo_navigation='Navigation condition: MCP is disabled. Use only Bob native repository search and file-read tools. Start with a targeted identifier search, narrow the candidate set, and verify the selected source files.'

run_case() {
    local prompt_id=$1
    local condition=$2
    local navigation=$3
    local prompt_file="$project_dir/prompts/$prompt_id.md"
    local output_file="$run_directory/${prompt_id}-${condition}.jsonl"
    local full_prompt
    local bob_exit

    printf -v full_prompt '%s\n\nTask:\n%s' "$navigation" "$(<"$prompt_file")"

    echo "[$prompt_id/$condition] Bob is running..." >&2
    set +e
    if [[ "$condition" == "solo" ]]; then
        BOB_API_KEY="$bob_key" bob run \
            --format stream-json \
            --workspace "$workspace_path" \
            --mode agent \
            --max-cost 0.80 \
            --max-turns 20 \
            --disable-mcp \
            --disable-subagents \
            --trust \
            --accept-license \
            --log-level error \
            "$full_prompt" 2>&1 \
            | BOB_REDACTION_SECRET="$bob_key" python3 "$script_dir/redact_stream.py" > "$output_file"
    else
        BOB_API_KEY="$bob_key" bob run \
            --format stream-json \
            --workspace "$workspace_path" \
            --mode agent \
            --max-cost 0.80 \
            --max-turns 20 \
            --disable-subagents \
            --trust \
            --accept-license \
            --log-level error \
            "$full_prompt" 2>&1 \
            | BOB_REDACTION_SECRET="$bob_key" python3 "$script_dir/redact_stream.py" > "$output_file"
    fi
    bob_exit=${PIPESTATUS[0]}
    set -e

    if [[ $bob_exit -ne 0 ]]; then
        echo "[$prompt_id/$condition] Bob exited $bob_exit" >&2
    fi
    if ! git -C "$workspace_path" diff --quiet --ignore-submodules; then
        echo "[$prompt_id/$condition] tracked workspace files changed during a read-only run" >&2
        exit 1
    fi
}

if [[ "$prompt_selector" == "all" || "$prompt_selector" == "01-feature-resolution" ]]; then
    if [[ "$case_selector" != "graph" ]]; then
        run_case 01-feature-resolution solo "$solo_navigation"
    fi
    if [[ "$case_selector" != "solo" ]]; then
        run_case 01-feature-resolution graph "$graph_navigation"
    fi
fi
if [[ "$prompt_selector" == "all" || "$prompt_selector" == "02-server-startup" ]]; then
    if [[ "$case_selector" != "solo" ]]; then
        run_case 02-server-startup graph "$graph_navigation"
    fi
    if [[ "$case_selector" != "graph" ]]; then
        run_case 02-server-startup solo "$solo_navigation"
    fi
fi

python3 "$script_dir/summarize.py" "$run_directory" "$workspace_path" > "$run_directory/summary.json"
unset bob_key

echo "$run_directory"
