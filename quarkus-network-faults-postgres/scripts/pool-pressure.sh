#!/usr/bin/env bash
set -euo pipefail

base_url=${1:-http://localhost:8097}
run_dir=$(mktemp -d)
trap 'rm -rf "$run_dir"' EXIT

printf 'Apply hangs:next-write in the Network faults Dev UI before running this command.\n'
curl -sS --max-time 20 -w '\nfirst: HTTP %{http_code}, %{time_total}s\n' \
    "$base_url/quotes/count" > "$run_dir/first.txt" &
first=$!
curl -sS --max-time 20 -w '\nsecond: HTTP %{http_code}, %{time_total}s\n' \
    "$base_url/quotes/count" > "$run_dir/second.txt" &
second=$!

occupied=false
for attempt in {1..30}; do
    pool=$(curl -fsS --max-time 2 "$base_url/lab/pool")
    case "$pool" in
        *'"active":2'*) occupied=true; break ;;
    esac
    sleep 0.1
done

if [ "$occupied" = true ]; then
    printf 'Pool before the third request: %s\n' "$pool"
    curl -sS --max-time 15 -w '\nthird: HTTP %{http_code}, %{time_total}s\n' "$base_url/quotes/count"
    curl -sS --max-time 3 -w '\npreview: HTTP %{http_code}, %{time_total}s\n' "$base_url/quotes/preview"
else
    printf 'Two connections were not occupied within the polling window. Check warm-up and the active fault.\n'
fi

wait "$first"
wait "$second"
cat "$run_dir/first.txt" "$run_dir/second.txt"
printf '\nEnd the fault in the Dev UI, then warm the pool again.\n'
