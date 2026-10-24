#!/bin/sh
set -eu

# Run from the companion root, or from a distribution containing quarkus-app/ and documents/.
export QUARKUS_DATASOURCE_JDBC_URL="${QUARKUS_DATASOURCE_JDBC_URL:-jdbc:postgresql://localhost:55432/rag}"
export QUARKUS_DATASOURCE_USERNAME="${QUARKUS_DATASOURCE_USERNAME:-rag_demo}"
export QUARKUS_DATASOURCE_PASSWORD="${QUARKUS_DATASOURCE_PASSWORD:-rag_demo}"
export QUARKUS_DOCLING_BASE_URL="${QUARKUS_DOCLING_BASE_URL:-http://localhost:55001}"
export QUARKUS_HTTP_HOST="${QUARKUS_HTTP_HOST:-127.0.0.1}"

if [ -f target/quarkus-app/quarkus-run.jar ]; then
    exec java -jar target/quarkus-app/quarkus-run.jar
fi
exec java -jar quarkus-app/quarkus-run.jar
