#!/usr/bin/env bash

set -euo pipefail

readonly CONTAINER_NAME="swiftship-postgres"
readonly IMAGE="docker.io/library/postgres:18.4-alpine3.24"
readonly VOLUME_NAME="swiftship-postgres-data"

start_database() {
    if podman container exists "${CONTAINER_NAME}"; then
        podman start "${CONTAINER_NAME}" >/dev/null
    else
        podman run --detach \
            --name "${CONTAINER_NAME}" \
            --publish 5432:5432 \
            --env POSTGRES_DB=swiftship \
            --env POSTGRES_USER=swiftship \
            --env POSTGRES_PASSWORD=swiftship \
            --health-cmd='pg_isready -U swiftship -d swiftship' \
            --health-interval=1s \
            --health-timeout=3s \
            --health-retries=30 \
            --volume "${VOLUME_NAME}:/var/lib/postgresql" \
            "${IMAGE}" >/dev/null
    fi

    for attempt in {1..30}; do
        if [[ "$(podman inspect --format '{{.State.Health.Status}}' "${CONTAINER_NAME}")" == "healthy" ]]; then
            echo "PostgreSQL is ready on localhost:5432"
            return
        fi
        sleep 1
    done

    podman logs "${CONTAINER_NAME}"
    echo "PostgreSQL did not become healthy within 30 seconds" >&2
    exit 1
}

case "${1:-start}" in
    start)
        start_database
        ;;
    stop)
        podman stop "${CONTAINER_NAME}" >/dev/null
        echo "PostgreSQL stopped"
        ;;
    status)
        podman inspect --format '{{.State.Status}} (health: {{.State.Health.Status}})' "${CONTAINER_NAME}"
        ;;
    *)
        echo "Usage: $0 {start|stop|status}" >&2
        exit 2
        ;;
esac
