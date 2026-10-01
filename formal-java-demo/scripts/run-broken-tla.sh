#!/usr/bin/env bash
set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/formal-common.sh"

printf 'Expected failure: TLC should report that NoOversell is violated (stock = -1).\n'
run_tlc InventoryBroken
