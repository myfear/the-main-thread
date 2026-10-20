#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
[[ $# -eq 0 ]] || die 'Usage: stop-db2.sh'
podman stop --time 60 "$DB2_CONTAINER"
printf '%s\n' 'Db2 stopped. The named volume is preserved.'
