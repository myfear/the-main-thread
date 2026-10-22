#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f target/quarkus-app/quarkus-run.jar ]]; then
  echo "Build first: ./mvnw package" >&2
  exit 1
fi
podman compose up -d
logging_jars=(target/quarkus-app/lib/boot/org.jboss.logging.jboss-logging-*.jar)
exec java -cp "target/quarkus-app/lib/main/*:${logging_jars[0]}" scripts/ReplicaDemo.java
