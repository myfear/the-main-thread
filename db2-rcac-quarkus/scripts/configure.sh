#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${1:-}" != --accept-license || $# != 1 ]]; then
    printf '%s\n' 'Review the Db2 Community image license:' \
        'https://www.ibm.com/docs/en/db2/12.1.x?topic=deployments-db2-community-edition-docker' \
        'After accepting it, run: ./scripts/configure.sh --accept-license' >&2
    exit 1
fi
[[ ! -e .env.db2 ]] || { printf '%s\n' '.env.db2 already exists; refusing to replace credentials.' >&2; exit 1; }
umask 077
{
    printf 'LICENSE=accept\n'
    for key in DB2INST1_PASSWORD DB2_A_PASSWORD DB2_B_PASSWORD DB2_A_PII_PASSWORD DB2_UNMAPPED_PASSWORD HTTP_ALICE_PASSWORD HTTP_BOB_PASSWORD HTTP_AUDITOR_PASSWORD HTTP_UNMAPPED_PASSWORD; do
        printf '%s=%s\n' "$key" "$(openssl rand -hex 16)"
    done
    printf 'DB2_PORT=50000\n'
} > .env.db2
printf '%s\n' 'Created ignored .env.db2 with private local credentials.'
