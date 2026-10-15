#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_compose_v2
command -v jq >/dev/null
podman info >/dev/null
mkdir -p target/siem infra/wazuh/config/wazuh_indexer_ssl_certs
touch target/siem/security-events.jsonl
# Local sample is intentionally readable by the manager's container user.
chmod 755 target/siem
chmod 644 target/siem/security-events.jsonl
cert_dir=infra/wazuh/config/wazuh_indexer_ssl_certs
complete=true
for cert in root-ca.pem root-ca-manager.pem admin.pem admin-key.pem wazuh.manager.pem wazuh.manager-key.pem wazuh.indexer.pem wazuh.indexer-key.pem wazuh.dashboard.pem wazuh.dashboard-key.pem; do
    if [[ ! -s "$cert_dir/$cert" ]]; then complete=false; fi
done
if [[ "$complete" == false ]]; then
    # The generator creates its keys in a directory shared with the Podman Linux host.
    podman compose --project-name quarkus-wazuh-certs -f infra/wazuh/generate-indexer-certs.yml run --rm generator
fi
if [[ "$(uname -s)" == Darwin ]]; then
    # Restore host-owner access after the generator's shared-mount permission changes.
    chmod 700 "$cert_dir"
    if [[ ! -s "$cert_dir/root-ca-manager.pem" && -s "$cert_dir/root-ca.pem" ]]; then
        cp "$cert_dir/root-ca.pem" "$cert_dir/root-ca-manager.pem"
    fi
    # Shared macOS files do not retain the Linux image's UID 1000 ownership.
    # Individual read-only mounts need readable PEM files; the host directory stays private.
    chmod 644 "$cert_dir"/*.pem
fi
for cert in root-ca.pem root-ca-manager.pem admin.pem admin-key.pem wazuh.manager.pem wazuh.manager-key.pem wazuh.indexer.pem wazuh.indexer-key.pem wazuh.dashboard.pem wazuh.dashboard-key.pem; do
    [[ -s "$cert_dir/$cert" ]] || { echo "Missing or unreadable generated certificate: $cert; check container UID mapping and host permissions" >&2; exit 1; }
done
compose config --quiet
echo "Prepared the audit directory and Wazuh TLS certificates. Start Quarkus with ./mvnw quarkus:dev."
