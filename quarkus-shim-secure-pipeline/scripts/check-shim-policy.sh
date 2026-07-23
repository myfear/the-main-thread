#!/bin/sh

set -eu

policy_file="${1:-shim-policy.yaml}"

if [ ! -f "$policy_file" ]; then
    echo "Missing shim policy: $policy_file" >&2
    exit 1
fi

expires_on=$(sed -n 's/^expires-on: //p' "$policy_file")
target_class=$(sed -n 's/^target-class: //p' "$policy_file")
target_method=$(sed -n 's/^target-method: //p' "$policy_file")

if [ -z "$expires_on" ] || [ -z "$target_class" ] || [ -z "$target_method" ]; then
    echo "Shim policy must declare expires-on, target-class, and target-method" >&2
    exit 1
fi

today_number=$(date -u +%Y%m%d)
expires_number=$(printf '%s' "$expires_on" | tr -d '-')

if [ "$today_number" -ge "$expires_number" ]; then
    echo "Shim policy expired on $expires_on" >&2
    exit 1
fi

echo "Shim policy is active until $expires_on"
