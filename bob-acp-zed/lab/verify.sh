#!/bin/sh

set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR="$PROJECT_DIR/target/classes"

mkdir -p "$BUILD_DIR"

javac \
    -d "$BUILD_DIR" \
    "$PROJECT_DIR/src/main/java/dev/mainthread/acp/TemperatureConverter.java" \
    "$PROJECT_DIR/src/test/java/dev/mainthread/acp/TemperatureConverterTest.java"

java -cp "$BUILD_DIR" dev.mainthread.acp.TemperatureConverterTest

echo "All checks passed"
