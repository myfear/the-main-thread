#!/usr/bin/env bash
set -euo pipefail
cd "$HOME/parallel-bob-lab"
case "${1:-}" in
    catalog) prompt=prompts/catalog-search.md; branch=feature/catalog-search ;;
    shipping) prompt=prompts/express-shipping.md; branch=feature/express-shipping ;;
    *) echo 'Usage: bash scripts/run-task.sh catalog|shipping' >&2; exit 2 ;;
esac
test "${BOB_API_KEY:-}" = bob-placeholder
test -z "$(git status --porcelain)"
git switch -c "$branch"
mkdir -p "$HOME/run-results"
# These output files stay outside the Git repository.
bob run --trust --accept-license --disable-mcp --disable-subagents \
    --max-turns 20 --max-cost 1 --format json \
    < "$prompt" > "$HOME/run-results/$1.json" 2> "$HOME/run-results/$1.stderr"
jq -e '.status == "success"' "$HOME/run-results/$1.json" >/dev/null
./mvnw -B test > "$HOME/run-results/$1-tests.log" 2>&1
git diff --check
git add src/main/java src/test/java
git commit -m "Implement $1 task in isolated Bob workstation"
git rev-parse HEAD
jq '{status, stats: {task_id: .stats.task_id, session_costs: .stats.session_costs, tool_calls: .stats.tool_calls, duration_ms: .stats.duration_ms}}' "$HOME/run-results/$1.json"
