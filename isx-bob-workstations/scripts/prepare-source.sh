#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -e .local/parallel-bob-lab ]]; then
    echo 'Source snapshot already exists. Preserve it or remove .local/parallel-bob-lab to start a new run.' >&2
    exit 1
fi
mkdir -p .local/parallel-bob-lab
for source in .mvn src prompts scripts .gitignore pom.xml mvnw mvnw.cmd; do
    cp -R "lab/$source" .local/parallel-bob-lab/
done
git -C .local/parallel-bob-lab init -b main
git -C .local/parallel-bob-lab config user.name 'Main Thread Bob Lab'
git -C .local/parallel-bob-lab config user.email 'bob-lab@example.invalid'
git -C .local/parallel-bob-lab config commit.gpgsign false
git -C .local/parallel-bob-lab add .
git -C .local/parallel-bob-lab commit -m 'Create unchanged catalog and shipping baseline'
git -C .local/parallel-bob-lab rev-parse HEAD > .local/baseline.txt
git -C .local/parallel-bob-lab bundle create ../baseline.bundle main
echo "Prepared baseline $(cat .local/baseline.txt)"
