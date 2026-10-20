#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test -f .local/baseline.txt
if [[ -e .local/review ]]; then
    echo 'Review checkout already exists; preserve it or choose a new run directory.' >&2
    exit 1
fi
git clone .local/baseline.bundle .local/review
git -C .local/review remote add catalog isx://mt-catalog-agent/~/parallel-bob-lab
git -C .local/review remote add shipping isx://mt-shipping-agent/~/parallel-bob-lab
git -C .local/review fetch catalog refs/heads/feature/catalog-search:refs/review/catalog
git -C .local/review fetch shipping refs/heads/feature/express-shipping:refs/review/shipping
git -C .local/review rev-parse refs/review/catalog > .local/catalog-commit.txt
git -C .local/review rev-parse refs/review/shipping > .local/shipping-commit.txt
baseline=$(cat .local/baseline.txt)
git -C .local/review merge-base --is-ancestor "$baseline" refs/review/catalog
git -C .local/review merge-base --is-ancestor "$baseline" refs/review/shipping
for task in catalog shipping; do
    git -C .local/review diff --check "$baseline" "refs/review/$task"
    git -C .local/review diff "$baseline" "refs/review/$task" > ".local/$task.diff"
    echo "$task: $(cat ".local/$task-commit.txt")"
    git -C .local/review diff --stat "$baseline" "refs/review/$task"
done
echo 'Read .local/catalog.diff and .local/shipping.diff before cherry-picking the saved commits.'
