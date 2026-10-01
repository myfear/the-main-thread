#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TLA_VERSION=1.7.4
TLA_SHA256=936a262061c914694dfd669a543be24573c45d5aa0ff20a8b96b23d01e050e88
TLA_JAR="$PROJECT_ROOT/.tools/tla/tla2tools.jar"

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

check_tla_checksum() {
    local actual
    if command -v sha256sum >/dev/null 2>&1; then
        actual="$(sha256sum "$1")"
    elif command -v shasum >/dev/null 2>&1; then
        actual="$(shasum -a 256 "$1")"
    else
        fail "TLA+ verification requires sha256sum or shasum on PATH."
    fi
    [[ "${actual%% *}" == "$TLA_SHA256" ]] ||
        fail "TLA+ SHA-256 mismatch for $1. Remove the file and retry."
}

bootstrap_tla() {
    if [[ ! -f "$TLA_JAR" ]]; then
        command -v curl >/dev/null 2>&1 || fail "Downloading TLA+ requires curl on PATH."
        mkdir -p "$(dirname -- "$TLA_JAR")"
        (
            temporary="$(mktemp "$TLA_JAR.XXXXXX")"
            trap 'rm -f -- "$temporary"' EXIT
            printf 'Downloading TLA+ %s\n' "$TLA_VERSION"
            curl --fail --location --silent --show-error --retry 3 \
                --connect-timeout 15 --max-time 120 \
                "https://github.com/tlaplus/tlaplus/releases/download/v$TLA_VERSION/tla2tools.jar" \
                --output "$temporary" || fail "Could not download TLA+ $TLA_VERSION. Check network access and retry."
            check_tla_checksum "$temporary"
            mv -- "$temporary" "$TLA_JAR"
        )
    fi
    check_tla_checksum "$TLA_JAR"
}

run_tlc() {
    local model="$1"
    local java_command=java
    if [[ -n "${JAVA_HOME:-}" ]]; then
        java_command="$JAVA_HOME/bin/java"
    fi
    command -v "$java_command" >/dev/null 2>&1 || fail "TLC requires Java. Set JAVA_HOME to JDK 21."
    bootstrap_tla
    mkdir -p "$PROJECT_ROOT/target/tlc/$model"
    printf 'Running TLC: %s\n' "$model"
    (
        cd "$PROJECT_ROOT/formal/tla"
        "$java_command" -XX:+UseParallelGC -jar "$TLA_JAR" \
            -workers 1 -seed 0 -metadir "$PROJECT_ROOT/target/tlc/$model" \
            -config "$model.cfg" "$model.tla"
    )
}
