#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY_DIR="${PROJECT_DIR}/src/main/resources/policies"
TEST_DIR="${PROJECT_DIR}/src/test/resources/policies"
BUNDLE="${PROJECT_DIR}/target/skill-admission-bundle.tar.gz"
EXTRACT_DIR="${PROJECT_DIR}/target/opa-compiled"

mkdir -p "${EXTRACT_DIR}"

podman run --rm \
  -v "${POLICY_DIR}:/policy:ro" \
  -v "${TEST_DIR}:/tests:ro" \
  openpolicyagent/opa:1.17.0 \
  test /policy /tests -v

podman run --rm \
  -v "${PROJECT_DIR}:/workspace" \
  -w /workspace \
  openpolicyagent/opa:1.17.0 \
  build -t wasm \
  -e fernbank/admission/decision \
  -o target/skill-admission-bundle.tar.gz \
  src/main/resources/policies/skill-admission.rego

tar -xzf "${BUNDLE}" -C "${EXTRACT_DIR}"
install -m 0644 "${EXTRACT_DIR}/policy.wasm" "${POLICY_DIR}/skill-admission.wasm"

echo "Wrote ${POLICY_DIR}/skill-admission.wasm"
