#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_compose_v2
./mvnw test -Dtest=WazuhPipelineTest -Dwazuh.verify=true "-Dwazuh.rotation=${WAZUH_VERIFY_ROTATION:-false}" "-Dwazuh.app-url=${APP_URL:-http://127.0.0.1:8088}"
if [[ "${WAZUH_VERIFY_ROTATION:-false}" == true ]]; then
    echo "Real rules and post-rollover indexing passed. Receipt: target/wazuh-verification/rotation-receipt.json"
else
    echo "Real rules, collection, indexed alerts and outage catch-up passed. Receipts: target/wazuh-verification/"
fi
echo "Open https://localhost:8443, log in as admin / SecretPassword, and filter Threat hunting by rule.groups:quarkus."
