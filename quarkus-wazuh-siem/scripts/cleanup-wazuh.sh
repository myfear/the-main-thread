#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_compose_v2
case "${1:-}" in
    --reset) compose down --volumes; echo "Removed this sample's Wazuh containers and state volumes; certificates and local audit file remain." ;;
    "") compose down; echo "Stopped this sample's Wazuh stack; state volumes remain." ;;
    *) echo "Usage: $0 [--reset]" >&2; exit 1 ;;
esac
