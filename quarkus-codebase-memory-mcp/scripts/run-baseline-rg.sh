#!/usr/bin/env bash
set -euo pipefail

QUARKUS_DIR="${1:-${QUARKUS_DIR:-$HOME/Projects/quarkus}}"

if [[ ! -d "$QUARKUS_DIR" ]]; then
  echo "Quarkus checkout not found at: $QUARKUS_DIR"
  exit 1
fi

echo "=== Baseline: rg for ConfigMappingBuildItem ==="
rg -n "ConfigMappingBuildItem" "$QUARKUS_DIR/core" "$QUARKUS_DIR/extensions/arc" \
  --glob '*.java' | head -40

echo
echo "=== Baseline: rg for ConfigMappingCreator ==="
rg -n "ConfigMappingCreator" "$QUARKUS_DIR" --glob '*.java' | head -20

echo
echo "=== Baseline: rg for discoverConfigMappings / generateConfigMappings ==="
rg -n "discoverConfigMappings|generateConfigMappings|registerConfigMappingBeans" "$QUARKUS_DIR" \
  --glob '*.java' | head -30
