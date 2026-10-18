#!/bin/sh
set -eu
app_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
: "${PROJECT_DIR:?Run scripts/prepare-demo.sh and export its output as PROJECT_DIR}"
: "${JBANG_BIN:?Set JBANG_BIN to the absolute JBang executable}"
cd "$app_dir"
export START_DEMO=true
exec "$JAVA_HOME/bin/java" -cp "$app_dir/target/quarkus-app/app/*:$app_dir/target/quarkus-app/lib/main/*:$app_dir/target/quarkus-app/lib/boot/*" \
    com.themainthread.codemode.ComparisonClient
