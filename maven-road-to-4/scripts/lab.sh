#!/bin/sh
set -eu
lab_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
    lab_java="$JAVA_HOME/bin/java"
else
    lab_java=java
fi
unset MAVEN_ARGS MAVEN_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
cd "$lab_root"
exec "$lab_java" --add-modules jdk.httpserver \
    lab-runner/src/main/java/com/themainthread/mavenlab/MavenLab.java "$@"
