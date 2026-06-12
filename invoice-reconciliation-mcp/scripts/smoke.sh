#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Running invoice-reconciliation-mcp tests..."
./mvnw test

echo "Smoke check passed."
