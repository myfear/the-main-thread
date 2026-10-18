#!/bin/sh
set -eu
app_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
: "${PROJECT_DIR:?Set PROJECT_DIR to the absolute Quarkus project directory}"
cd "$app_dir"
worker_cp="$app_dir/target/quarkus-app/app/*:$app_dir/target/quarkus-app/lib/main/*:$app_dir/target/quarkus-app/lib/boot/*"
if [ -n "${UPSTREAM_JAR:-}" ]; then
    set -- "-Dgateway.upstream-jar=$UPSTREAM_JAR"
else
    : "${JBANG_BIN:?Set JBANG_BIN to an absolute JBang executable, or set UPSTREAM_JAR}"
    set -- "-Dgateway.jbang=$JBANG_BIN"
fi
exec "$JAVA_HOME/bin/java" -Xmx256m "$@" \
    "-Dgateway.project=$PROJECT_DIR" "-Dgateway.session=${SESSION_DIR:-$app_dir/.local/session}" \
    "-Dgateway.start-demo=${START_DEMO:-false}" "-Dgateway.demo-port=${DEMO_PORT:-18191}" \
    "-Dcodemode.worker.classpath=$worker_cp" -jar target/quarkus-app/quarkus-run.jar
