#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ ! -f .env ]]; then
  echo 'Copy .env.example to .env first.' >&2
  exit 1
fi
set -a
source .env
set +a
export PODMAN_COMPOSE_WARNING_LOGS=false
CONNECT_URL="http://127.0.0.1:${CONNECT_PORT:-28083}"
APP_URL="http://127.0.0.1:${APP_PORT:-18086}"
compose() { podman compose "$@"; }
sql() { compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U checkout -d checkout "$@"; }
wait_for() {
  local description=$1
  shift
  for ((attempt=0; attempt<120; attempt++)); do
    if "$@"; then return 0; fi
    sleep 1
  done
  echo "Timed out waiting for $description" >&2
  return 1
}
