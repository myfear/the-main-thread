#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
target="${1:-$root}"
checks="$root/checks"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

javac --release 25 -d "$out" -sourcepath "$checks:$target/src/main/java" \
  "$checks/com/themainthread/payroll/BoundaryChecks.java"
java -cp "$out" com.themainthread.payroll.BoundaryChecks
