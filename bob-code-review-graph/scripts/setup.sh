#!/usr/bin/env bash
set -euo pipefail

open_liberty_sha=3da6c82529721046a4a6a73f07b34c5c57f8d76e
crg_version=2.3.8
crg_tools=get_minimal_context_tool,query_graph_tool,traverse_graph_tool,semantic_search_nodes_tool,list_graph_stats_tool,get_impact_radius_tool,get_review_context_tool

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "usage: $0 OPEN_LIBERTY_CHECKOUT [CRG_VENV]" >&2
    exit 2
fi

checkout=$1
venv=${2:-"$checkout-crg-venv"}

command -v bob >/dev/null
command -v git >/dev/null
command -v uv >/dev/null

if [[ ! -e "$checkout" ]]; then
    git init "$checkout"
    git -C "$checkout" remote add origin https://github.com/OpenLiberty/open-liberty.git
    git -C "$checkout" fetch --depth 1 origin "$open_liberty_sha"
    git -C "$checkout" checkout --detach FETCH_HEAD
elif [[ ! -d "$checkout/.git" ]]; then
    echo "$checkout exists but is not a Git checkout" >&2
    exit 1
fi

actual_sha=$(git -C "$checkout" rev-parse HEAD)
if [[ "$actual_sha" != "$open_liberty_sha" ]]; then
    echo "expected Open Liberty $open_liberty_sha, found $actual_sha" >&2
    exit 1
fi

if [[ ! -x "$venv/bin/python" ]]; then
    uv venv --python 3.14 "$venv"
fi
uv pip install --python "$venv/bin/python" "code-review-graph==$crg_version"

crg=$(cd "$venv/bin" && pwd)/code-review-graph
checkout=$(cd "$checkout" && pwd)

time "$crg" build --repo "$checkout"
"$crg" status --repo "$checkout"
mkdir -p "$checkout/.bob"

(
    cd "$checkout"
    bob mcp add \
        --scope workspace \
        --transport stdio \
        code-review-graph \
        "$crg" \
        -- serve --repo "$checkout" --tools "$crg_tools"
)

echo "Configured code-review-graph for Bob in $checkout/.bob/mcp.json"
