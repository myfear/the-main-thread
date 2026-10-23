#!/usr/bin/env bash
set -euo pipefail

# OpenShell 0.1.5 and a running Podman machine must be available on PATH.
lab_root=$(cd "$(dirname "$0")/.." && pwd)
export XDG_CONFIG_HOME="$lab_root/.local/config"
export XDG_STATE_HOME="$lab_root/.local/state"
export OPENSHELL_TELEMETRY_ENABLED=false
lab_tls="$XDG_STATE_HOME/openshell/tls"
umask 077
mkdir -p "$XDG_CONFIG_HOME" "$XDG_STATE_HOME"

if [[ ! -f "$lab_tls/ca.crt" ]]; then
    openshell-gateway generate-certs --output-dir "$lab_tls" \
        --server-san 127.0.0.1 --server-san localhost \
        --server-san host.containers.internal --server-san 192.168.127.254
fi

cat > "$XDG_CONFIG_HOME/gateway.toml" <<EOF
[openshell]
version = 2

[openshell.gateway]
name = "bob-tutorial"
bind_address = "127.0.0.1:18771"
compute_driver = "podman"
guest_tls_ca = '$lab_tls/ca.crt'

[openshell.gateway.tls]
cert_path = '$lab_tls/server/tls.crt'
key_path = '$lab_tls/server/tls.key'
client_ca_path = '$lab_tls/ca.crt'

[openshell.gateway.mtls_auth]
enabled = true
EOF

openshell-gateway config preflight --path "$XDG_CONFIG_HOME/gateway.toml"
exec openshell-gateway --config "$XDG_CONFIG_HOME/gateway.toml"
