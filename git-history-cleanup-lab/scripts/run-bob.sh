#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd -P)"
repo="${1:-$root/work/payroll-lab}"
condition="${2:-full}"

case "$condition" in
  full|docs-only|source-only) ;;
  *) echo "Use full, docs-only, or source-only." >&2; exit 1 ;;
esac
if [ ! -d "$repo/.git" ]; then
  echo "No lab repository at $repo. Run ./scripts/assemble.sh first." >&2
  exit 1
fi
repo="$(cd "$repo" && pwd -P)"
baseline="$(git -C "$repo" rev-parse --verify refs/heads/main)"
if [ -z "${BOB_API_KEY:-}" ]; then
  echo "Set BOB_API_KEY before running this script." >&2
  exit 1
fi
command -v bob >/dev/null

# Each run gets the same committed main, regardless of the reader's checkout.
# Keep evidence outside the agent workspace, including a copy for diffing.
mkdir -p "$root/work/bob-runs"
run="$(mktemp -d "$root/work/bob-runs/$condition.XXXXXX")"
# A no-history workspace inside this checkout could still discover its parent
# repository. Keep every agent workspace in the OS temporary directory instead.
workspace="$(mktemp -d "${TMPDIR:-/tmp}/main-thread-bob.XXXXXX")"
workspace="$(cd "$workspace" && pwd -P)"
if git -C "$workspace" rev-parse --show-toplevel >/dev/null 2>&1; then
  echo "Temporary directory is inside a Git repository; choose another TMPDIR." >&2
  exit 1
fi
mkdir -p "$run/baseline"
git -C "$repo" archive --format=tar "$baseline" > "$run/baseline.tar"
tar -xf "$run/baseline.tar" -C "$run/baseline"
rm "$run/baseline.tar"

if [ "$condition" = full ]; then
  git clone -q --no-local --single-branch --branch main "$repo" "$workspace"
  git -C "$workspace" remote remove origin
else
  cp -R "$run/baseline/src" "$run/baseline/README.md" \
    "$run/baseline/.gitattributes" "$workspace/"
  if [ "$condition" = docs-only ]; then
    cp -R "$run/baseline/docs" "$workspace/"
  fi
fi
mkdir -p "$workspace/scripts"
cp "$root/scripts/check.sh" "$workspace/scripts/"
if [ "$condition" = full ]; then
  echo '/scripts/' >> "$workspace/.git/info/exclude"
fi
cp "$root/prompts/cleanup.txt" "$run/prompt.txt"
{
  echo "condition=$condition"
  echo "baseline=$baseline"
  echo "workspace=$workspace"
  echo "max_turns=${BOB_MAX_TURNS:-15}"
  echo "max_cost_bobcoins=${BOB_MAX_COST:-0.60}"
  bob --version
} > "$run/metadata.txt"

echo "Running $condition; results will be kept at $run" >&2
status=0
# Run from the workspace too, so the caller's directory is not Bob's cwd.
(cd "$workspace" && bob run --accept-license --trust --disable-mcp --disable-subagents \
  --max-turns "${BOB_MAX_TURNS:-15}" --max-cost "${BOB_MAX_COST:-0.60}" \
  --format json --workspace "$workspace" "$(cat "$run/prompt.txt")") \
  > "$run/result.json" 2> "$run/stderr.log" || status=$?
echo "process_exit_status=$status" >> "$run/metadata.txt"
# Bob 2.0.9 can emit an error event followed by status=success and exit 0
# when its turn cap is reached. Preserve the raw output and fail the wrapper.
if grep -Eq '"type"[[:space:]]*:[[:space:]]*"error"|"status"[[:space:]]*:[[:space:]]*"(error|failed)"' "$run/result.json"; then
  echo 'output_error=1' >> "$run/metadata.txt"
  if [ "$status" -eq 0 ]; then status=1; fi
fi
echo "exit_status=$status" >> "$run/metadata.txt"

diff_status=0
diff -ru "$run/baseline/src" "$workspace/src" > "$run/source.diff" || diff_status=$?
if [ "$diff_status" -gt 1 ]; then
  echo "Could not compare source trees; inspect $run." >&2
  exit "$diff_status"
fi
check_status=0
"$root/scripts/check.sh" "$workspace" > "$run/checks.log" 2>&1 || check_status=$?
echo "check_status=$check_status" >> "$run/metadata.txt"
echo "Results kept at $run (Bob exit $status, checks exit $check_status)." >&2
if [ "$status" -ne 0 ]; then exit "$status"; fi
exit "$check_status"
