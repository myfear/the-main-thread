#!/bin/sh
set -eu

# These two ephemeral containers belong only to the packaged local demo.
podman run --detach --rm --name main-thread-rag-pgvector \
    --publish 127.0.0.1:55432:5432 \
    --env POSTGRES_DB=rag --env POSTGRES_USER=rag_demo --env POSTGRES_PASSWORD=rag_demo \
    docker.io/pgvector/pgvector:pg17

podman run --detach --rm --name main-thread-rag-docling \
    --publish 127.0.0.1:55001:5001 \
    ghcr.io/docling-project/docling-serve-cpu:v1.35.0

attempt=0
until podman exec main-thread-rag-pgvector pg_isready -U rag_demo -d rag >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 60 ]; then
        echo 'PostgreSQL did not become ready; inspect podman logs main-thread-rag-pgvector' >&2
        exit 1
    fi
    sleep 1
done
podman exec main-thread-rag-pgvector psql -U rag_demo -d rag -v ON_ERROR_STOP=1 \
    -c 'CREATE EXTENSION IF NOT EXISTS vector'

attempt=0
until curl --fail --silent http://localhost:55001/health >/dev/null; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 150 ]; then
        echo 'Docling did not become ready; inspect podman logs main-thread-rag-docling' >&2
        exit 1
    fi
    sleep 2
done
echo 'Packaged-demo services ready on localhost:55432 and localhost:55001'
