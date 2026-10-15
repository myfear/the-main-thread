#!/usr/bin/env bash
# Sourced by the scripts below. Requires Bash, Podman, and a Compose V2 provider.
set -euo pipefail
PROJECT_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$PROJECT_ROOT"

compose() {
    podman compose --project-name quarkus-wazuh-siem -f infra/wazuh/compose.yml "$@"
}

require_compose_v2() {
    local version
    version=$(podman compose version --short)
    case "$version" in
        2.*|v2.*) ;;
        *) echo "Select a standalone Compose V2 provider with PODMAN_COMPOSE_PROVIDER; got: $version" >&2; exit 1 ;;
    esac
}
