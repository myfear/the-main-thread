#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
target="${1:-$root}"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

javac --release 25 -d "$out" \
  "$target"/src/main/java/com/themainthread/payroll/*.java \
  "$target"/src/test/java/com/themainthread/payroll/*.java
java -cp "$out" com.themainthread.payroll.Checks
if [ -f "$target/src/test/java/com/themainthread/payroll/PayrollExportChecks.java" ]; then
  java -cp "$out" com.themainthread.payroll.PayrollExportChecks
fi
