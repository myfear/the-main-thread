#!/usr/bin/env bash
set -euo pipefail

echo "Removing Keycloak Dev Services containers and volumes (Podman)..."

while read -r id; do
  [[ -n "$id" ]] || continue
  podman rm -fv "$id" >/dev/null && echo "Removed $id (with volumes)"
done < <(podman ps -aq --filter label=io.quarkus.devservices.keycloak 2>/dev/null || true)

while read -r line; do
  id="${line%% *}"
  [[ -n "$id" ]] || continue
  if podman port "$id" 2>/dev/null | grep -q 8180; then
    podman rm -fv "$id" >/dev/null && echo "Removed Keycloak container $id (with volumes)"
  fi
done < <(podman ps -aq --format '{{.ID}} {{.Names}}' 2>/dev/null || true)

echo "Done. Start loan-service with ./mvnw quarkus:dev to import a fresh loanflow realm."
