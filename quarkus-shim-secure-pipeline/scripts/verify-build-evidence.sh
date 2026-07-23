#!/bin/sh

set -eu

shim_dump=$(find policy-service/target/shim -type f -name '*LegacyDecisionEngine*.txt' -size +0c -print -quit)
sbom=$(find policy-service/target -type f -name '*cyclonedx.json' -print -quit)

if [ -z "$shim_dump" ]; then
    echo "No transformed-class dump found for LegacyDecisionEngine" >&2
    exit 1
fi

if [ -z "$sbom" ]; then
    echo "No CycloneDX SBOM found" >&2
    exit 1
fi

if ! grep -Fq 'access-policy-sdk' "$sbom"; then
    echo "Vendor dependency is missing from $sbom" >&2
    exit 1
fi

echo "Verified transformed class: $shim_dump"
echo "Verified vendor dependency in SBOM: $sbom"
