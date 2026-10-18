#!/bin/sh
set -eu
app_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
cd "$app_dir"
if [ "$#" -eq 0 ]; then
    set -- examples/investigate.js examples/triage.js examples/incident-report.js examples/diagnostic.js
fi
exec "$JAVA_HOME/bin/java" -cp "$app_dir/target/quarkus-app/app/*:$app_dir/target/quarkus-app/lib/main/*:$app_dir/target/quarkus-app/lib/boot/*" \
    com.themainthread.codemode.ExampleClient "$@"
