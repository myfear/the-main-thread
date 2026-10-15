#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_compose_v2
# The image imports configuration into a persistent volume. Existing rule files are retained.
compose exec -T wazuh.manager sh -c 'cp /wazuh-config-mount/etc/ossec.conf /var/ossec/etc/ossec.conf && cp /wazuh-config-mount/etc/rules/quarkus_rules.xml /var/ossec/etc/rules/quarkus_rules.xml && chown wazuh:wazuh /var/ossec/etc/ossec.conf /var/ossec/etc/rules/quarkus_rules.xml && /var/ossec/bin/wazuh-control restart'
echo "Manager configuration reloaded. Wait for its health check, then run ./scripts/verify-wazuh.sh."
