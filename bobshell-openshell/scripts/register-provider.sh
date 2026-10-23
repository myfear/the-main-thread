#!/usr/bin/env bash
set -euo pipefail
set +x

# Read from the terminal; the secret is never a command-line argument.
IFS= read -r -s -p 'Bob Inference API key: ' BOB_API_KEY
printf '\n'
if [[ -z "$BOB_API_KEY" ]]; then
    printf 'No API key supplied.\n' >&2
    exit 1
fi
export BOB_API_KEY
openshell provider create --name bob-test --type ibm-bob --credential BOB_API_KEY
